package it.interno.mattinale.chat.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import it.interno.mattinale.chat.ai.model.QueryPlan;
import it.interno.mattinale.chat.ai.util.JsonConverters;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

@Service
public class QueryPlannerService {
  private final ChatModel chatModel;
  private final org.springframework.ai.chat.memory.ChatMemory chatMemory;
  private final JsonConverters jsonConverters;

  public QueryPlannerService(ChatModel chatModel,
                             org.springframework.ai.chat.memory.ChatMemory chatMemory, JsonConverters jsonConverters) {
    this.chatModel = chatModel;
    this.chatMemory = chatMemory;
      this.jsonConverters = jsonConverters;
  }

  public QueryPlan plan(String userQuery, String conversationId) throws JsonProcessingException {
    // 1. Retrieve history
    java.util.List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(conversationId);
    // Optional: Slice history to last 10 messages if needed
    history.forEach(message -> {System.out.println("[ChatOrchestrator] History Message : " + message.getText());});
    if (history.size() > 10) {
      history = history.subList(history.size() - 10, history.size());
    }
    var outputConverter = new BeanOutputConverter<>(QueryPlan.class);
    // 2. Prepare System Message
    var systemMessage = new org.springframework.ai.chat.messages.SystemMessage(
        """
                Sei un assistente che estrae filtri di ricerca da domande in linguaggio naturale.
                Data una query utente e la cronologia della conversazione, determina i filtri aggiornati per recuperare gli articoli pertinenti.
                Rispondi sempre in formato json conforme a RFC8259 che segua il formato specificato alla fine.
                    Non includere blocchi di codice markdown nella tua risposta,senza testo extra.

                  REGOLE CRITICHE:
                  1. Considera la cronologia della conversazione.
                  2. Se l'utente chiede una modifica (es. "cambia data", "cerca a Milano"), AGGIORNA solo quel filtro e MANTIENI i filtri precedenti invariati.
                  3. Restituisci SEMPRE un oggetto JSON completo con TUTTI i filtri attivi (sia quelli nuovi che quelli mantenuti dalla storia).
                  4. Se un filtro non è mai stato menzionato, non includerlo.
                  5. Se l'utente dice "oggi" o "ieri", converti in data ISO.
                  6. Se l'utente chiede quali sono le funzionalità, cosa puoi fare, o aiuto sulle richieste, usa userIntent: CAPABILITIES.

                Rispondi in formato JSON con i seguenti campi:
                 - requiresSql: booleano
                 - requiresVector: booleano
                 - userIntent: stringa (FULL_ARTICLE,COUNT,TREND,COMPARE,MIN_MAX,DETAIL,CHART,CAPABILITIES)
                 - dateRange: intervallo di date, se applicabile in formato ISO "yyyy-MM-dd" (es. "2025-11-15"),se l'utente chiede un singolo giorno, fornisci un intervallo con la stessa data di inizio e fine, usa una lista con tre elementi [from,to,days],rispondi con json valido. esempio {"from":"2025-11-01","to":"2025-11-15","days":15} se non specificato null
             - section: stringa (fullArticle,organico,fattiDiRilievo,denunciati,arrestati,pattuglie,
                                     servizi,immigrazione,controlliAmministrativiQuestura,reati,misurePrevenzione,sequestriQuestura,attiviPrevenzTerritorioQuestura,
                                     attiviPrevenzUfficiInvestigativiQuestura,attiviPrevenzAltriUfficiQuestura,attiviPrevenzCrimine,perquisizioni,monitoraggioWeb,oscuramentoWeb,noscWeb,crimineEconFinanOnLine,attiviPrevenzTerritorioCosc) se non specificato null
             - source: stringa (questura,polizia stradale,polfer,cosc,frontiera) se non specificato null
             - province: stringa usa sempre la città in maiuscolo (es. "ROMA", "MILANO","NAPOLI","TORINO")
             - contentType: stringa (section, fullArticle, summary) se non specificato null
             
             FORMATO :
                 {format}
            """.replace("{format}", outputConverter.getFormat()));

    // 3. Prepare User Message
    var userMessage = new org.springframework.ai.chat.messages.UserMessage(userQuery);

    // 4. Build Prompt
    // The prompt should contain: System Message + History + New User Message
    java.util.List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
    messages.add(systemMessage);
    messages.addAll(history);
    messages.add(userMessage);



    QueryPlan queryPlanResponse = ChatClient.create(chatModel)
            .prompt().messages(messages)
            .advisors(new SimpleLoggerAdvisor())
            .call()
            .entity(QueryPlan.class);

    // 6. Save to Memory (User Query and AI Response)
    // We save the raw JSON response as the AI's "thought" so it remembers the plan
    // it made.
    assert queryPlanResponse != null;
    chatMemory.add(conversationId,
            java.util.List.of(userMessage, new org.springframework.ai.chat.messages.AssistantMessage(jsonConverters.toJson(queryPlanResponse))));

      System.out.println("[QueryPlannerService] JSON Response: " + queryPlanResponse);

   return queryPlanResponse;
  }

}

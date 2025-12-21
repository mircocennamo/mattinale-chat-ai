package it.interno.mattinale.chat.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import it.interno.mattinale.chat.ai.model.QueryPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatOrchestrator {
    private final QueryPlannerService queryPlannerService;
    private final VectorService vectorService;
    private final AnswerService answerService;
    private final ChatModel chatModel;

    public ChatOrchestrator(QueryPlannerService queryPlannerService, VectorService vectorService,
            AnswerService answerService, ChatModel chatModel) {
        this.queryPlannerService = queryPlannerService;
        this.vectorService = vectorService;
        this.answerService = answerService;
        this.chatModel = chatModel;
    }

    /**
     * Esegue il ciclo completo RAG:
     * 1. Recupera documenti contestuali da Oracle 23ai.
     * 2. Invia i documenti e la domanda a OpenAI.
     * 3. Ritorna la risposta generata.
     *
     * @param question La domanda dell'utente.
     * @return La risposta generata dall'LLM.
     */
    public ChatResponse handleQuery(String question, String conversationId) throws JsonProcessingException {
        // 1. Pianificazione
        System.out.println("[ChatOrchestrator] Question : " + question);
        System.out.println("[ChatOrchestrator] Conversation Id : " + conversationId);
        QueryPlan queryPlan = queryPlannerService.plan(question, conversationId);

        System.out.println("[ChatOrchestrator] Plan: " + queryPlan);

        // 2. Esecuzione query/tool
        // if (Boolean.TRUE.equals(queryPlan.requiresVector())) {
        List<Document> documents = new ArrayList<>(vectorService.search(queryPlan));
       // }

        // 3. Generazione risposta
        // Passiamo l'intent al servizio di risposta per guidare la scelta del tool
        // (opzionale)
        return answerService.generate(queryPlan,question, documents, queryPlan.userIntent());
    }

    public String extractFilterFromNL(String text) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return chatClient.prompt().user(text)
                .system("""
                        Sei un assistente che estrae filtri di ricerca da domande in linguaggio naturale.
                         Fornisci la risposta da poter poi inserire nel filter del VectorStore.
                        Dato un testo in italiano, estrai i seguenti filtri se presenti:
                        - province: nome della provincia in maiuscolo (es. "ROMA", "MILANO")
                        - data: in formato ISO "yyyy-MM-dd" (es. "2025-11-15")
                        - source : questura,polizia stradale,polfer,cosc,frontiera
                        - section : arrestati, denunciati, pattuglie, servizi, immigrazione, controlliAmministrativiQuestura, reati, misurePrevenzione, sequestriQuestura, attiviPrevenzTerritorioQuestura, attiviPrevenzUfficiInvestigativiQuestura, attiviPrevenzAltriUfficiQuestura, attiviPrevenzCrimine
                        - partial: true/false
                            Ad esempio, se l'utente chiede "Mattinale della questura di Roma del  15/11/2025 con dati parziali sezione pattuglia?",
                        la risposta sarà:
                         province == 'ROMA' AND date == '2025-11-15' AND partial == true AND source == 'questura' AND section == 'pattuglie'

                          Ad esempio, se l'utente chiede "Mattinale della polfer o polizia ferroviaria di Roma del  15/11/2025 con dati parziali?",
                        la risposta sarà:
                         province == 'ROMA' AND date == '2025-11-15' AND partial == true AND source == 'polfer'

                         Ad esempio, se l'utente chiede "Mattinale cosc di Roma del  15/11/2025 con dati parziali sezione organico?",
                        la risposta sarà:
                         province == 'ROMA' AND date == '2025-11-15' AND partial == true AND source == 'cosc' AND section == 'organico'

                          Ad esempio, se l'utente chiede "Mattinale della polizia stradale o polizia di Roma del  15/11/2025 con dati parziali?",
                        la risposta sarà:
                         province == 'ROMA' AND date == '2025-11-15' AND partial == true AND source == 'polizia stradale'

                         Ad esempio, se l'utente chiede "Mattinale della frontiera di Roma del  15/11/2025 con dati parziali?",
                        la risposta sarà:
                         province == 'ROMA' AND date == '2025-11-15' AND partial == true AND source == 'frontiera'

                        Se un filtro non è presente, non includerlo nella risposta.
                        Se l'utente dice "oggi" o "ieri", converti in data ISO

                        Se nessun filtro è presente, rispondi con stringa vuota.
                        """)
                .call().content();
    }

    private String sanitizeFilterExpression(String filter) {
        if (filter == null)
            return null;

        String f = filter.trim();

        // Rimuovi eventuali doppi apici ai lati: "…"
        if (f.startsWith("\"") && f.endsWith("\"")) {
            f = f.substring(1, f.length() - 1);
        }

        // Sostituisci && / || con AND / OR
        f = f.replace("&&", "AND").replace("||", "OR");

        // Rimuovi virgolette tipografiche
        f = f.replace("“", "'").replace("”", "'");

        // Uniforma chiavi: content-type -> contentType (evita il trattino)
        f = f.replace("content-type", "contentType");

        // Facoltativo: normalizza provincia/section/date
        f = f.replace("provincia", "province")
                .replace("data", "date") // se usi 'date' nei metadata
                .replace("sezione", "section");

        return f;
    }
}

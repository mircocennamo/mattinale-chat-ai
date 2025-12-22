package it.interno.mattinale.chat.ai.service;

import it.interno.mattinale.chat.ai.component.Oracle23AiTools;
import it.interno.mattinale.chat.ai.enumeration.UserIntent;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import it.interno.mattinale.chat.ai.model.QueryPlan;
import it.interno.mattinale.chat.ai.tool.ChartTools;
import it.interno.mattinale.chat.ai.util.JsonConverters;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static it.interno.mattinale.chat.ai.util.ToolsMapping.getTools;

@Service
public class AnswerService {
    private final ChatModel chatModel;

    @Value("classpath:templates/prompt2.st")
    private Resource getInfoTemplate;

    private final JsonConverters jsonConverters;

    private final ChartTools chartTools;
    private final Oracle23AiTools oracle23AiTools;

    public AnswerService(ChatModel chatModel, JsonConverters jsonConverters, ChartTools chartTools,
            Oracle23AiTools oracle23AiTools) {
        this.chatModel = chatModel;
        this.jsonConverters = jsonConverters;
        this.chartTools = chartTools;
        this.oracle23AiTools = oracle23AiTools;
    }

    public ChatResponse generate(QueryPlan queryPlan,String question, List<Document> docs,
            it.interno.mattinale.chat.ai.enumeration.UserIntent intent) {

        System.out.println("[RagService] Generating answer for question: " + question);
        System.out.println("[RagService] size docs: " + docs.size());
        if (intent == null) {
            System.out.println("[RagService] Intent is null value, setting to DETAIL");

            intent = UserIntent.DETAIL;
        }

        System.out.println("[RagService] intent: " + intent.name());

        if (intent == UserIntent.CAPABILITIES) {
            System.out.println("[RagService] Handling CAPABILITIES intent. Injecting system capabilities doc.");
            String capabilitiesText = """
                Il sistema Mattinale Chat AI è progettato per analizzare e interrogare i dati dei Mattinali di Polizia.
                Ecco le principali tipologie di richieste che puoi effettuare:
                
                1. **Ricerca e Dettaglio**:
                   - "Mostrami i fatti di rilievo di Roma di oggi"
                   - "Cerca mattinali della Polizia Stradale a Milano del 15/11/2025"
                
                2. **Conteggi e Statistiche**:
                   - "Quanti arrestati ci sono stati a Napoli ieri?"
                   - "Conta le pattuglie a Torino nell'ultima settimana"
                
                3. **Analisi dei Trend**:
                   - "Mostrami l'andamento dei reati a Roma negli ultimi 10 giorni"
                   - "Fammi vedere il trend delle denunce a Firenze"
                
                4. **Confronti**:
                   - "Confronta i dati di oggi con quelli di ieri a Bologna"
                   - "Differenze tra il 1 novembre e il 2 novembre a Venezia"
                
                5. **Minimi e Massimi**:
                   - "In quale giorno ci sono stati più arresti a Palermo?"
                   - "Quando c'è stato il minimo di pattuglie a Genova?"
                
                6. **Grafici**:
                   - "Genera un grafico degli arrestati a Roma nell'ultimo mese"
                
                Puoi filtrare per:
                - **Provincia**: (es. Roma, Milano)
                - **Data**: (oggi, ieri, data specifica)
                - **Sezione**: (arrestati, denunciati, pattuglie, ecc.)
                - **Fonte**: (Questura, Polfer, Stradale, ecc.)
                """;
            docs = new java.util.ArrayList<>();
            docs.add(new Document(capabilitiesText));
        }

        PromptTemplate promptTemplate = new PromptTemplate(getInfoTemplate);

        String documents = docs.stream()
                .map(Document::getText)
                .collect(java.util.stream.Collectors.joining("\n\n"));

        System.out.println("[RagService] documents: " + documents);

        // (opzionale) aggiungi le "sorgenti" al prompt
        String sources = docs.stream()
                .map(doc -> formatSource(doc.getMetadata()))
                .collect(java.util.stream.Collectors.joining("\n"));

        System.out.println("[RagService] sources: " + sources);

        java.util.List<String> validTools = it.interno.mattinale.chat.ai.util.ToolsMapping.INTENT_TO_TOOLS.get(intent);
        String toolGuidance = "";
        if (validTools != null && !validTools.isEmpty()) {
            toolGuidance = "Per soddisfare la richiesta, utilizza uno dei seguenti tool: "
                    + String.join(", ", validTools);
            System.out.println("[RagService] toolGuidance: " + toolGuidance);
        }
        var outputConverter = new BeanOutputConverter<>(ChatResponse.class);

        Message systemMessage = promptTemplate.createMessage(Map.of(
                "documents", documents,
                "sources", sources,
                "format", outputConverter.getFormat(),
                "tool_guidance", toolGuidance));

        Message userMessage = new UserMessage(question);

        // Inietta le opzioni nel Prompt
        Prompt prompt = new Prompt(java.util.List.of(systemMessage, userMessage));
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = ChatClient.create(chatModel).prompt(prompt);
        ChatResponse chatResponse;
        if(queryPlan!=null && !UserIntent.CAPABILITIES.equals(queryPlan.userIntent())) {
            System.out.println("[RagService] Tool Context: " + queryPlan);
            Map<String,Object> queryPlanMap = getTools(queryPlan);
            chatClientRequestSpec.toolContext(queryPlanMap);
             chatResponse = chatClientRequestSpec
                    .tools(chartTools, oracle23AiTools)
                    .advisors(new SimpleLoggerAdvisor())
                    .call()
                    .entity(ChatResponse.class);
            if (chatResponse != null && (chatResponse.getText() == null ||
                    chatResponse.getText().toLowerCase().contains("non disponibile") ||
                    chatResponse.getText().toLowerCase().contains("non ho trovato") ||
                    chatResponse.getText().toLowerCase().contains("nessun dato"))) {
                System.out.println("[RagService] Detected empty/negative response, adding suggestions.");
                chatResponse.setSuggestions(java.util.List.of(
                        new it.interno.mattinale.chat.ai.model.Suggestion("Vuoi cambiare la data?", "CHANGE_DATE", "date"),
                        new it.interno.mattinale.chat.ai.model.Suggestion("Vuoi cercare per un'altra provincia?",
                                "CHANGE_PROVINCE", "province"),
                        new it.interno.mattinale.chat.ai.model.Suggestion("Vuoi cercare per una sezione diversa?",
                                "CHANGE_SECTION", "section")));
            }

        }else{
             chatResponse = chatClientRequestSpec
                    .advisors(new SimpleLoggerAdvisor())
                    .call()
                    .entity(ChatResponse.class);
        }


        System.out.println("[RagService] Response: " + chatResponse);


        return chatResponse;
    }

    private String formatSource(Map<String, Object> md) {
        if (md == null || md.isEmpty()) {
            return "Fonte: [n/a]";
        }
        String date = asStr(md.get("date"));
        String province = asStr(md.get("province"));
        String section = asStr(md.get("section"));
        String contentType = asStr(md.get("contentType"));
        String partial = String.valueOf(md.getOrDefault("partial", false));

        // opzionali, utili per tracing
        String id = asStr(md.get("id")); // se lo imposti in ingest
        String source = asStr(md.get("source")); // es. "json"
        String file = asStr(md.get("file")); // es. "questuraIns-2025-11-01.json"
        String tags = asListStr(md.get("tags")); // se usi una lista di tag

        StringBuilder sb = new StringBuilder();
        sb.append("Fonte: [");
        if (!id.isBlank())
            sb.append("id=").append(id).append(" | ");
        if (!source.isBlank())
            sb.append("src=").append(source).append(" | ");
        if (!file.isBlank())
            sb.append("file=").append(file).append(" | ");
        if (!date.isBlank())
            sb.append("date=").append(date).append(" | ");
        if (!province.isBlank())
            sb.append("prov=").append(province).append(" | ");
        if (!section.isBlank())
            sb.append("section=").append(section).append(" | ");
        if (!contentType.isBlank())
            sb.append("contentType=").append(contentType).append(" | ");
        sb.append("partial=").append(partial);
        if (!tags.isBlank())
            sb.append(" | tags=").append(tags);
        sb.append("]");

        return sb.toString();
    }

    /**
     * Converte null -> "" altrimenti toString().
     */
    private String asStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * Converte liste/array in "a,b,c"; se non lista, usa toString().
     */
    @SuppressWarnings("unchecked")
    private String asListStr(Object v) {
        if (v == null)
            return "";
        if (v instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder();
            for (Object o : it) {
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(asStr(o));
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

}

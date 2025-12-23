package it.interno.mattinale.chat.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.interno.mattinale.chat.ai.component.*;
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
import java.util.stream.Collectors;

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

        System.out.println("[AnswerService] Generating answer for question: " + question);
        System.out.println("[AnswerService] size docs: " + docs.size());
        if (intent == null) {
            System.out.println("[AnswerService] Intent is null value, setting to DETAIL");

            intent = UserIntent.DETAIL;
        }

        System.out.println("[AnswerService] intent: " + intent.name());

        if (intent == UserIntent.CAPABILITIES) {
            System.out.println("[AnswerService] Handling CAPABILITIES intent. Injecting system capabilities doc.");
            String capabilitiesText = """
                Il sistema Mattinale Chat AI è progettato per analizzare e interrogare i dati dei Mattinali di Polizia.
                Rispondi sempre in formato json conforme a RFC8259 che segua il formato specificato alla fine.
                Non includere blocchi di codice markdown nella tua risposta,senza testo extra.
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
                
                Rispondi sempre in formato JSON come nell'esempio seguente:
                {
                  "receivedAt": "2025-11-01",
                  "text": "Risposta testuale qui",
                  "suggestions": [
                    {
                      "title": "Cambia data",
                      "action": "CHANGE_DATE",
                      "parameter": "date"
                    },
                    {
                      "title": "Cambia provincia",
                      "action": "CHANGE_PROVINCE",
                      "parameter": "province"
                    }
                  ]
                }
                
                """;
            docs = new java.util.ArrayList<>();
            docs.add(new Document(capabilitiesText));
        }

        PromptTemplate promptTemplate = new PromptTemplate(getInfoTemplate);

        String documents = docs.stream()
                .map(Document::getText)
                .collect(java.util.stream.Collectors.joining("\n\n"));

        System.out.println("[AnswerService] documents: " + documents);

        // (opzionale) aggiungi le "sorgenti" al prompt
        String sources = docs.stream()
                .map(doc -> formatSource(doc.getMetadata()))
                .collect(java.util.stream.Collectors.joining("\n"));

        System.out.println("[AnswerService] sources: " + sources);


        String toolGuidance = buildToolGuidanceForTrend(intent);

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
            System.out.println("[AnswerService] Tool Context: " + queryPlan);
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
                System.out.println("[AnswerService] Detected empty/negative response, adding suggestions.");
                chatResponse.setSuggestions(java.util.List.of(
                        new it.interno.mattinale.chat.ai.model.Suggestion("Vuoi cambiare la data?", "CHANGE_DATE", "date"),
                        new it.interno.mattinale.chat.ai.model.Suggestion("Vuoi cercare per un'altra provincia?",
                                "CHANGE_PROVINCE", "province"),
                        new it.interno.mattinale.chat.ai.model.Suggestion("Vuoi cercare per una sezione diversa?",
                                "CHANGE_SECTION", "section")));
            }

        }else{
            /*

 .advisors(
        new org.springframework.ai.chat.client.advisor.FunctionCallingAdvisor(oracle23AiTools),
        new org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor()
    );

             */
             chatResponse = chatClientRequestSpec
                    .advisors(new SimpleLoggerAdvisor())
                    .call()
                    .entity(ChatResponse.class);
        }


        System.out.println("[AnswerService] Response: " + chatResponse);


        return chatResponse;
    }
//NON CANCELLARE
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







// ...

    /**
     * Renderizza l’andamento temporale (trend) combinando i documenti vettoriali
     * con un documento sintetico JSON dei punti temporali.
     *
     * @param plan     QueryPlan di riferimento
     * @param question domanda utente
     * @param docs     documenti vettoriali (RAG context)
     * @param points   output del tool SQL getTemporalTrend (lista di TrendPointDto)
     * @return ChatResponse generata dall’LLM
     */
    public ChatResponse renderTrend(QueryPlan plan,
                                    String question,
                                    List<Document> docs,
                                    List<TrendPointDto> points,UserIntent intent) {

        System.out.println("[AnswerService] renderTrend: question=" + question
                + ", docs=" + (docs == null ? 0 : docs.size())
                + ", points=" + (points == null ? 0 : points.size()));


// Supponiamo: jsonConverters.toJson(Point p) -> String JSON
        assert points != null;
        List<String> jsonList = points.stream()
                .map(jsonConverters::toJson)   // <-- method reference al posto di toJson()
                .toList();



        // 1) Crea un documento sintetico (JSON) con i punti del trend
        Document trendDoc = buildTrendSyntheticDocument(plan, points);

        // 2) Combina i documents vettoriali con il documento sintetico
        List<Document> allDocs = new java.util.ArrayList<>();
        if (docs != null && !docs.isEmpty()) {
            allDocs.addAll(docs);
        }
        allDocs.add(trendDoc);

        return generate(plan, question, allDocs, intent);
     }

    /**
     * Costruisce un Document sintetico JSON dai punti del trend.
     * Inserisce anche metadata utili (intent, province, section, source, dateFrom/dateTo).
     */
    private Document buildTrendSyntheticDocument(QueryPlan plan, List<TrendPointDto> points) {
        try {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            ObjectNode root = mapper.createObjectNode();

            root.put("type", "sql-result");
            root.put("intent", "TREND");

            if (plan != null) {
                if (plan.province() != null) root.put("province", plan.province());
                if (plan.section()  != null) root.put("section",  plan.section());
                if (plan.source()   != null) root.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    root.put("dateFrom", String.valueOf(plan.dateRange().from()));
                    root.put("dateTo", String.valueOf(plan.dateRange().to()));
                    if (plan.dateRange().days() != null) {
                        root.put("days", plan.dateRange().days());
                    }
                }
            }

            ArrayNode series = mapper.createArrayNode();
            if (points != null) {
                for (TrendPointDto p : points) {
                    ObjectNode row = mapper.createObjectNode();
                    // Assumo TrendPointDto(day, total) con day=LocalDate, total=long/int
                    row.put("day", p.day() == null ? "" : p.day().toString());
                    // Conversione safe del totale
                    long val;
                    try { val = (long) p.total(); } catch (Exception e) { val = 0L; }
                    row.put("total", val);
                    series.add(row);
                }
            }
            root.set("data", series);

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

            // Metadata del Document (no Map.of per evitare null)
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "TREND");
            if (plan != null) {
                md.put("province", plan.province());
                md.put("section",  plan.section());
                md.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    md.put("dateFrom", plan.dateRange().from());
                    md.put("dateTo",   plan.dateRange().to());
                    md.put("days",     plan.dateRange().days());
                }
            }
            return new Document(json, md);
        } catch (Exception e) {
            // In caso di errore, rientra con un documento minimo
            String fallback = "Trend: dati non disponibili (" + e.getMessage() + ")";
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "TREND");
            return new Document(fallback, md);
        }
    }

    /**
     * Costruisce una guidance testuale per forzare l’uso dei tool in richieste strutturate.
     */
    //NON CANCELLARE
    private String buildToolGuidanceForTrend(UserIntent intent) {
        List<String> validTools = it.interno.mattinale.chat.ai.util.ToolsMapping.INTENT_TO_TOOLS
                .get(intent);
        if (validTools == null || validTools.isEmpty()) {
            return "Per i calcoli di andamento temporale usa gli strumenti disponibili nel contesto.";
        }
        return "Per l'andamento temporale DEVI invocare uno dei seguenti tool: "
                + String.join(", ", validTools)
                + ". Usa il ToolContext fornito.";
    }







// ===============================================
// RENDER COMPARE: per compareTwoDates (ComparisonPointDto)
// ===============================================

    /**
     * Renderizza il confronto (COMPARE) combinando eventuali documenti vettoriali
     * con un documento sintetico JSON che contiene i record di confronto restituiti
     * dal tool SQL (compareTwoDates).
     *
     * @param plan     QueryPlan di riferimento
     * @param question domanda utente
     * @param docs     documenti vettoriali (RAG context)
     * @param points   output del tool SQL compareTwoDates (lista di ComparisonPointDto)
     * @return ChatResponse generata dall’LLM
     */
    public ChatResponse renderCompare(QueryPlan plan,
                                      String question,
                                      List<Document> docs,
                                      List<ComparisonPointDto> points,UserIntent intent) {

        System.out.println("[AnswerService] renderCompare: question=" + question
                + ", docs=" + (docs == null ? 0 : docs.size())
                + ", points=" + (points == null ? 0 : points.size()));

        // 1) Crea un documento sintetico (JSON) con i dati del confronto
        Document cmpDoc = buildCompareSyntheticDocument(plan, points);

        // 2) Combina i documents vettoriali con il documento sintetico
        List<Document> allDocs = new java.util.ArrayList<>();
        if (docs != null && !docs.isEmpty()) {
            allDocs.addAll(docs);
        }
        allDocs.add(cmpDoc);


        return generate(plan, question, allDocs, intent);

    }

    /**
     * Costruisce un Document sintetico JSON dal risultato del confronto.
     * - type: "sql-result"
     * - intent: "COMPARE"
     * - summary: conteggio per giorno
     * - data: lista di elementi con day, content, metadata
     *
     * NB: content/metadata nel DB sono stringhe; qui proviamo a riconoscere JSON validi
     *     e in quel caso li includiamo come oggetti, altrimenti come stringa raw.
     */
    private Document buildCompareSyntheticDocument(QueryPlan plan, List<ComparisonPointDto> points) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            ObjectNode root = mapper.createObjectNode();
            root.put("type", "sql-result");
            root.put("intent", "COMPARE");

            // Metadati dal QueryPlan
            if (plan != null) {
                if (plan.province() != null) root.put("province", plan.province());
                if (plan.section()  != null) root.put("section",  plan.section());
                if (plan.source()   != null) root.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    root.put("dateFrom", String.valueOf(plan.dateRange().from()));
                    root.put("dateTo", String.valueOf(plan.dateRange().to()));
                    if (plan.dateRange().days() != null) {
                        root.put("days", plan.dateRange().days());
                    }
                }
            }

            // Summary (conteggio per giorno)
            java.util.Map<String, Integer> totalsByDay = new java.util.HashMap<>();
            if (points != null) {
                for (ComparisonPointDto p : points) {
                    String dayStr = p.day() == null ? "" : p.day().toString();
                    totalsByDay.merge(dayStr, 1, Integer::sum);
                }
            }
            ObjectNode summary = mapper.createObjectNode();
            for (var e : totalsByDay.entrySet()) {
                summary.put(e.getKey(), e.getValue()); // es. "2025-11-01": 12
            }
            root.set("summary", summary);

            // Data array
            ArrayNode data = mapper.createArrayNode();
            if (points != null) {
                for (ComparisonPointDto p : points) {
                    ObjectNode row = mapper.createObjectNode();
                    row.put("day", p.day() == null ? "" : p.day().toString());

                    // Tenta di interpretare content/metadata come JSON, altrimenti string
                    // CONTENT
                    boolean contentAdded = false;
                    String contentStr = p.content();
                    if (contentStr != null && !contentStr.isBlank()) {
                        try {
                            var contentNode = mapper.readTree(contentStr);
                            row.set("contentJson", contentNode);
                            contentAdded = true;
                        } catch (Exception ignore) { /* non è JSON */ }
                    }
                    if (!contentAdded) {
                        row.put("contentRaw", contentStr == null ? "" : contentStr);
                    }

                    // METADATA
                    boolean mdAdded = false;
                    String mdStr = p.metadata();
                    if (mdStr != null && !mdStr.isBlank()) {
                        try {
                            var mdNode = mapper.readTree(mdStr);
                            row.set("metadataJson", mdNode);
                            mdAdded = true;
                        } catch (Exception ignore) { /* non è JSON */ }
                    }
                    if (!mdAdded) {
                        row.put("metadataRaw", mdStr == null ? "" : mdStr);
                    }

                    data.add(row);
                }
            }
            root.set("data", data);

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

            // Metadata del Document sintetico
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "COMPARE");
            if (plan != null) {
                md.put("province", plan.province());
                md.put("section",  plan.section());
                md.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    md.put("dateFrom", plan.dateRange().from());
                    md.put("dateTo",   plan.dateRange().to());
                    md.put("days",     plan.dateRange().days());
                }
            }
            return new Document(json, md);

        } catch (Exception e) {
            String fallback = "Compare: dati non disponibili (" + e.getMessage() + ")";
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "COMPARE");
            return new Document(fallback, md);
        }
    }











// ===============================================
// RENDER COUNT
// ===============================================

    /**
     * Renderizza un risultato di conteggio (COUNT) combinando eventuali documenti
     * vettoriali con un documento sintetico JSON { type=sql-result, intent=COUNT, value=count }.
     *
     * @param plan     QueryPlan di riferimento (province/section/source/date range)
     * @param question domanda utente
     * @param docs     documenti vettoriali (RAG context)
     * @param count    risultato numerico del tool SQL countByFilter
     * @return ChatResponse generata dall’LLM
     */
    public ChatResponse renderCount(QueryPlan plan,
                                    String question,
                                    List<Document> docs,
                                    int count,UserIntent intent) {

        System.out.println("[AnswerService] renderCount: question=" + question
                + ", docs=" + (docs == null ? 0 : docs.size())
                + ", count=" + count);

        // 1) Documento sintetico con il valore di conteggio
        Document countDoc = buildCountSyntheticDocument(plan, count);

        // 2) Combina docs vettoriali + documento COUNT
        List<Document> allDocs = new java.util.ArrayList<>();
        if (docs != null && !docs.isEmpty()) {
            allDocs.addAll(docs);
        }
        allDocs.add(countDoc);

        return generate(plan, question, allDocs, intent);


    }

    /**
     * Costruisce un Document sintetico JSON per il conteggio.
     * Struttura:
     * {
     *   "type": "sql-result",
     *   "intent": "COUNT",
     *   "value": <count>,
     *   "province": "...", "section": "...", "source": "...",
     *   "dateFrom": "...", "dateTo": "...", "days": <n>
     * }
     */
    private Document buildCountSyntheticDocument(QueryPlan plan, int count) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            ObjectNode root = mapper.createObjectNode();
            root.put("type", "sql-result");
            root.put("intent", "COUNT");
            root.put("value", count);

            if (plan != null) {
                if (plan.province() != null) root.put("province", plan.province());
                if (plan.section()  != null) root.put("section",  plan.section());
                if (plan.source()   != null) root.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    root.put("dateFrom", String.valueOf(plan.dateRange().from()));
                    root.put("dateTo", String.valueOf(plan.dateRange().to()));
                    if (plan.dateRange().days() != null) {
                        root.put("days", plan.dateRange().days());
                    }
                }
            }

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

            // Metadata del Document (usa HashMap per evitare problemi con null)
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "COUNT");
            if (plan != null) {
                md.put("province", plan.province());
                md.put("section",  plan.section());
                md.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    md.put("dateFrom", plan.dateRange().from());
                    md.put("dateTo",   plan.dateRange().to());
                    md.put("days",     plan.dateRange().days());
                }
            }
            return new Document(json, md);

        } catch (Exception e) {
            String fallback = "Count: dati non disponibili (" + e.getMessage() + ")";
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "COUNT");
            return new Document(fallback, md);
        }
    }







// =======================================================
// RENDER MIN/MAX
// =======================================================

    /**
     * Renderizza il risultato MIN/MAX combinando i documenti vettoriali
     * con un documento sintetico JSON che contiene i punti (day,total)
     * e i riepiloghi min/max.
     *
     * @param plan     QueryPlan di riferimento
     * @param question domanda utente
     * @param docs     documenti vettoriali (RAG context)
     * @param mm       lista di MinMaxDto (esito del tool getMinMaxByPeriod)
     * @return ChatResponse generata dall’LLM
     */
    public ChatResponse renderMinMax(QueryPlan plan,
                                     String question,
                                     List<Document> docs,
                                     List<MinMaxDto> mm,UserIntent intent) {

        System.out.println("[AnswerService] renderMinMax: question=" + question
                + ", docs=" + (docs == null ? 0 : docs.size())
                + ", points=" + (mm == null ? 0 : mm.size()));

        // 1) Costruisci documento sintetico JSON con min/max + dati
        Document minMaxDoc = buildMinMaxSyntheticDocument(plan, mm);

        // 2) Combina con eventuali documenti vettoriali
        List<Document> allDocs = new java.util.ArrayList<>();
        if (docs != null && !docs.isEmpty()) {
            allDocs.addAll(docs);
        }
        allDocs.add(minMaxDoc);

        return generate(plan, question, allDocs, intent);

    }

    /**
     * Costruisce un Document sintetico JSON con:
     * - "type": "sql-result"
     * - "intent": "MIN_MAX"
     * - "summary": { "min": {"day":..., "total":...}, "max": {...} }
     * - "data": [ {"day": "...", "total": N}, ... ]
     *
     * Se la lista non è ordinata, calcoliamo min/max in Java.
     * Se la lista è già ordinata (es. query con ORDER BY total DESC),
     * usiamo il primo come "max" e l’ultimo come "min".
     */
    private Document buildMinMaxSyntheticDocument(QueryPlan plan, List<MinMaxDto> mm) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            ObjectNode root = mapper.createObjectNode();
            root.put("type", "sql-result");
            root.put("intent", "MIN_MAX");

            // Metadati di contesto
            if (plan != null) {
                if (plan.province() != null) root.put("province", plan.province());
                if (plan.section()  != null) root.put("section",  plan.section());
                if (plan.source()   != null) root.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    root.put("dateFrom", String.valueOf(plan.dateRange().from()));
                    root.put("dateTo", String.valueOf(plan.dateRange().to()));
                    if (plan.dateRange().days() != null) {
                        root.put("days", plan.dateRange().days());
                    }
                }
            }

            // Data array
            ArrayNode data = mapper.createArrayNode();
            if (mm != null) {
                for (MinMaxDto p : mm) {
                    ObjectNode row = mapper.createObjectNode();
                    row.put("day", p.day() == null ? "" : p.day().toString());
                    // total potrebbe essere int o long: gestiamo in modo safe
                    long totalVal;
                    try { totalVal = (long) p.total(); } catch (Exception e) { totalVal = 0L; }
                    row.put("total", totalVal);
                    data.add(row);
                }
            }
            root.set("data", data);

            // Summary min/max
            ObjectNode summary = mapper.createObjectNode();
            ObjectNode minNode = mapper.createObjectNode();
            ObjectNode maxNode = mapper.createObjectNode();

            if (mm != null && !mm.isEmpty()) {
                // Se pensi che la tua query sia già ordinata per total DESC:
                // max = primo, min = ultimo
                // Altrimenti calcola in Java.
                MinMaxDto minDto = null, maxDto = null;

                // Rilevamento ordine: se >1 elemento, confronta primi
                boolean assumeDesc = false;
                if (mm.size() > 1) {
                    try {
                        long t0 = (long) mm.get(0).total();
                        long t1 = (long) mm.get(1).total();
                        assumeDesc = t0 >= t1; // euristica semplice
                    } catch (Exception ignore) {}
                }

                if (assumeDesc) {
                    maxDto = mm.get(0);
                    minDto = mm.get(mm.size() - 1);
                } else {
                    // Calcolo robusto
                    for (MinMaxDto p : mm) {
                        if (p == null) continue;
                        long val;
                        try { val = (long) p.total(); } catch (Exception e) { val = 0L; }
                        if (maxDto == null || val > (long) maxDto.total()) {
                            maxDto = p;
                        }
                        if (minDto == null || val < (long) minDto.total()) {
                            minDto = p;
                        }
                    }
                }

                if (maxDto != null) {
                    maxNode.put("day",   maxDto.day() == null ? "" : maxDto.day().toString());
                    long v; try { v = (long) maxDto.total(); } catch (Exception e) { v = 0L; }
                    maxNode.put("total", v);
                }
                if (minDto != null) {
                    minNode.put("day",   minDto.day() == null ? "" : minDto.day().toString());
                    long v; try { v = (long) minDto.total(); } catch (Exception e) { v = 0L; }
                    minNode.put("total", v);
                }
            }

            summary.set("min", minNode);
            summary.set("max", maxNode);
            root.set("summary", summary);

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

            // Metadata del Document sintetico
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "MIN_MAX");
            if (plan != null) {
                md.put("province", plan.province());
                md.put("section",  plan.section());
                md.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    md.put("dateFrom", plan.dateRange().from());
                    md.put("dateTo",   plan.dateRange().to());
                    md.put("days",     plan.dateRange().days());
                }
            }
            return new Document(json, md);

        } catch (Exception e) {
            String fallback = "Min/Max: dati non disponibili (" + e.getMessage() + ")";
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "MIN_MAX");
            return new Document(fallback, md);
        }
    }









// =======================================================
// RENDER DRILL-DOWN (DETAIL / FULL_ARTICLE)
// =======================================================

    /**
     * Renderizza il risultato di drill-down (dettaglio contenuti), combinando i documenti
     * vettoriali con un documento sintetico JSON costruito dalla lista di DocumentDto
     * ottenuta dai tool SQL (getDocumentsByFilter / getFullArticleByFilter).
     *
     * @param plan     QueryPlan di riferimento
     * @param question domanda utente
     * @param docs     documenti vettoriali (RAG context)
     * @param list     lista di DocumentDto (content, metadata)
     * @return ChatResponse generata dall’LLM
     */
    public ChatResponse renderDrillDown(QueryPlan plan,
                                        String question,
                                        List<Document> docs,
                                        List<DocumentDto> list,UserIntent intent) {

        System.out.println("[AnswerService] renderDrillDown: question=" + question
                + ", docs=" + (docs == null ? 0 : docs.size())
                + ", items=" + (list == null ? 0 : list.size()));

        // 1) Documento sintetico JSON dai risultati di drill-down
        Document drillDoc = buildDrillDownSyntheticDocument(plan, list);

        // 2) Combina con eventuali documents vettoriali
        List<Document> allDocs = new java.util.ArrayList<>();
        if (docs != null && !docs.isEmpty()) {
            allDocs.addAll(docs);
        }
        allDocs.add(drillDoc);

        return generate(plan, question, allDocs, intent);


    }

    /**
     * Costruisce un Document sintetico JSON per il drill-down.
     * Struttura:
     * {
     *   "type": "sql-result",
     *   "intent": "DETAIL",
     *   "filters": { province, section, source, dateFrom, dateTo, days },
     *   "count": <nRows>,
     *   "items": [
     *      { "contentJson" | "contentRaw", "metadataJson" | "metadataRaw" },
     *      ...
     *   ]
     * }
     * Se content/metadata sono JSON validi, vengono inclusi come oggetti; altrimenti come stringhe raw.
     */
    private Document buildDrillDownSyntheticDocument(QueryPlan plan, List<DocumentDto> list) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            ObjectNode root = mapper.createObjectNode();
            root.put("type", "sql-result");
            root.put("intent", "DETAIL");

            // Filtri / contesto
            ObjectNode filters = mapper.createObjectNode();
            if (plan != null) {
                if (plan.province() != null) filters.put("province", plan.province());
                if (plan.section()  != null) filters.put("section",  plan.section());
                if (plan.source()   != null) filters.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    filters.put("dateFrom", String.valueOf(plan.dateRange().from()));
                    filters.put("dateTo", String.valueOf(plan.dateRange().to()));
                    if (plan.dateRange().days() != null) {
                        filters.put("days", plan.dateRange().days());
                    }
                }
            }
            root.set("filters", filters);

            // Lista items
            ArrayNode items = mapper.createArrayNode();
            int n = 0;
            if (list != null) {
                for (DocumentDto d : list) {
                    ObjectNode row = mapper.createObjectNode();

                    // content (JSON o raw)
                    boolean contentAdded = false;
                    String contentStr = d.content();
                    if (contentStr != null && !contentStr.isBlank()) {
                        try {
                            var contentNode = mapper.readTree(contentStr);
                            row.set("contentJson", contentNode);
                            contentAdded = true;
                        } catch (Exception ignore) { /* non è JSON */ }
                    }
                    if (!contentAdded) {
                        row.put("contentRaw", contentStr == null ? "" : contentStr);
                    }

                    // metadata (JSON o raw)
                    boolean mdAdded = false;
                    String mdStr = d.metadata();
                    if (mdStr != null && !mdStr.isBlank()) {
                        try {
                            var mdNode = mapper.readTree(mdStr);
                            row.set("metadataJson", mdNode);
                            mdAdded = true;
                        } catch (Exception ignore) { /* non è JSON */ }
                    }
                    if (!mdAdded) {
                        row.put("metadataRaw", mdStr == null ? "" : mdStr);
                    }

                    items.add(row);
                    n++;
                }
            }
            root.set("items", items);
            root.put("count", n);

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

            // Metadata del Document sintetico (usa HashMap per tollerare null)
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "DETAIL");
            if (plan != null) {
                md.put("province", plan.province());
                md.put("section",  plan.section());
                md.put("source",   plan.source());
                if (plan.dateRange() != null) {
                    md.put("dateFrom", plan.dateRange().from());
                    md.put("dateTo",   plan.dateRange().to());
                    md.put("days",     plan.dateRange().days());
                }
            }

            return new Document(json, md);

        } catch (Exception e) {
            String fallback = "Drill-down: dati non disponibili (" + e.getMessage() + ")";
            Map<String, Object> md = new java.util.HashMap<>();
            md.put("type", "sql-result");
            md.put("intent", "DETAIL");
            return new Document(fallback, md);
        }
    }


}

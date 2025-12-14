package demo.elastic.rag;


import demo.elastic.rag.model.FilterResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import demo.elastic.rag.util.ProvinceDictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagService {


    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final ProvinceDictionary provinceDictionary;
    private final ChatClient chatClient;


    @Value("classpath:templates/prompt2.st")
    private Resource getInfoTemplate;

    // Parametri di ricerca configurabili (application.properties / env)
    @Value("${rag.search.topK:1}")
    private int defaultTopK;

    @Value("${rag.search.similarityThreshold:0.0}")
    private double similarityThreshold;

    // Nota: alcune implementazioni non supportano MMR; per compatibilità non lo abilitiamo qui.

    /**
     * Costruttore con iniezione di dipendenza.
     * Configura il ChatClient per usare l'advisor RAG (RetrievalAdvisor)
     * che si connette al VectorStore (Oracle 23ai).
     */
    public RagService(VectorStore vectorStore, ChatModel chatModel, ProvinceDictionary provinceDictionary, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.provinceDictionary = provinceDictionary;
        this.chatClient = chatClient;
    }



    public String extractFilterFromNL(String text) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return chatClient.prompt().user(text).system("""
                Sei un assistente che estrae filtri di ricerca da domande in linguaggio naturale.
                Dato un testo in italiano, estrai i seguenti filtri se presenti:
                - provincia: nome della provincia in maiuscolo (es. "ROMA", "MILANO")
                - data: in formato ISO "yyyy-MM-dd" (es. "2025-11-15")
                Fornisci la risposta da poter poi inserire nel filter del VectorStore.
                - sezione: una delle seguenti sezioni: arrestati, denunciati, pattuglie, servizi, immigrazione, controlliAmministrativiQuestura, reati, misurePrevenzione, sequestriQuestura, attiviPrevenzTerritorioQuestura, attiviPrevenzUfficiInvestigativiQuestura, attiviPrevenzAltriUfficiQuestura, attiviPrevenzCrimine
                - datiParziali: true/false
                Ad esempio, se l'utente chiede "Mattinale della questura di Roma del  15/11/2025 con dati parziali?",
                la risposta sarà:  
                 province == 'ROMA' AND date == '2025-11-15' AND partial == true
                Se un filtro non è presente, non includerlo nella risposta.
                Se l'utente dice "oggi" o "ieri", converti in data ISO
                Usa il dizionario delle province per mappare nomi comuni a sigle ufficiali.
                Se nessun filtro è presente, rispondi con stringa vuota.
                """).call().content();
    }



    public void ingestPDF(Resource resource) {
        // 1) Reader PDF: 1 pagina => 1 Document (così è facile valorizzare pageNumber/filename)
        var cfg = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .withPageExtractedTextFormatter(
                        ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .withNumberOfBottomTextLinesToDelete(0)
                                .build())
                .build();

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, cfg);

        // 2) Leggi
        List<Document> docs = pdfReader.read(); // Document(text, metadata) per pagina [3](https://piotrminkowski.com/2025/03/13/tool-calling-with-spring-ai/)

        // 3) Arricchisci i metadati: usa builder() per creare nuovi Document con metadata uniti
        String fileName = Optional.ofNullable(resource.getFilename()).orElse("unknown.pdf");

       List<Document> enriched = docs.stream().map(d -> {
            Map<String, Object> base = d.getMetadata() == null ? Map.of() : d.getMetadata();

            // Unisci metadati esistenti con i nuovi
            Map<String, Object> merged = new HashMap<>(base);
            merged.putIfAbsent(PagePdfDocumentReader.METADATA_FILE_NAME, fileName);
            merged.putIfAbsent("source", "pdf");
            merged.putIfAbsent(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, base.getOrDefault("pageNumber", 0)); // se già presente, lo mantiene


            // Ricrea il Document con builder (testo + metadati). Puoi anche conservare l'ID:
            return Document.builder()
                    .id(d.getId())          // mantiene l'id esistente, se presente
                    .text(d.getText())      // contenuto testuale
                    .metadata(merged)       // mappa completa di metadati
                    .build();
        }).toList();


        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(enriched);
        // 5) Scrivi nel VectorStore a batch (evita spike di memoria/latency su file grandi)
        addInBatches(chunks, 200); // API VectorStore add/similaritySearch/filter [5](https://github.com/alibaba/spring-ai-alibaba)
    }

      /**
         * Esegue il ciclo completo RAG:
         * 1. Recupera documenti contestuali da Oracle 23ai.
         * 2. Invia i documenti e la domanda a OpenAI.
         * 3. Ritorna la risposta generata.
         * @param question La domanda dell'utente.
         * @return La risposta generata dall'LLM.
         */


      private String sanitizeFilterExpression(String filter) {
          if (filter == null) return null;

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
                  .replace("data", "date")  // se usi 'date' nei metadata
                  .replace("sezione", "section");

          return f;
      }


    /**
     * Restituisce una stringa con i metadati principali del Document.
     * Atteso che nei metadata siano presenti: date (ISO), province, section, contentType, partial.
     */
    private String formatSource(Map<String, Object> md) {
        if (md == null || md.isEmpty()) {
            return "Fonte: [n/a]";
        }
        String date        = asStr(md.get("date"));
        String province    = asStr(md.get("province"));
        String section     = asStr(md.get("section"));
        String contentType = asStr(md.get("contentType"));
        String partial     = String.valueOf(md.getOrDefault("partial", false));

        // opzionali, utili per tracing
        String id          = asStr(md.get("id"));           // se lo imposti in ingest
        String source      = asStr(md.get("source"));       // es. "json"
        String file        = asStr(md.get("file"));         // es. "questuraIns-2025-11-01.json"
        String tags        = asListStr(md.get("tags"));     // se usi una lista di tag

        StringBuilder sb = new StringBuilder();
        sb.append("Fonte: [");
        if (!id.isBlank())       sb.append("id=").append(id).append(" | ");
        if (!source.isBlank())   sb.append("src=").append(source).append(" | ");
        if (!file.isBlank())     sb.append("file=").append(file).append(" | ");
        if (!date.isBlank())     sb.append("date=").append(date).append(" | ");
        if (!province.isBlank()) sb.append("prov=").append(province).append(" | ");
        if (!section.isBlank())  sb.append("section=").append(section).append(" | ");
        if (!contentType.isBlank()) sb.append("contentType=").append(contentType).append(" | ");
        sb.append("partial=").append(partial);
        if (!tags.isBlank())     sb.append(" | tags=").append(tags);
        sb.append("]");

        return sb.toString();
    }

    /** Converte null -> "" altrimenti toString(). */
    private String asStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** Converte liste/array in "a,b,c"; se non lista, usa toString(). */
    @SuppressWarnings("unchecked")
    private String asListStr(Object v) {
        if (v == null) return "";
        if (v instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder();
            for (Object o : it) {
                if (sb.length() > 0) sb.append(",");
                sb.append(asStr(o));
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }




    public String generateAnswer(String question) {
          // 1) Costruisci la query testuale per l'embedding (usa la domanda così com'è)
          String query = question;

          // 2) Estrai e sanifica il filtro (AND/OR, apici singoli, rimuovi doppi apici esterni)
          String rawFilter = extractFilterFromNL(question);
          String filter = sanitizeFilterExpression(rawFilter);

          System.out.println("[RagService] filter: " + filter);

          // 3) Esegui la similarity search con query + filtro + parametri ragionevoli
          List<Document> results = vectorStore.similaritySearch(
                  SearchRequest.builder()
                          .query(query)                   // ⚠️ necessario!
                          .filterExpression(filter)       // es.: "province == 'ROMA' AND section == 'arrestati'"
                          .topK(6)                        // recupera più contesto
                         // .similarityThreshold(0.70)      // opzionale: riduci se il dominio è vario
                          .build()
          );

          if (results == null || results.isEmpty()) {
              return "Nessun documento trovato.";
          }


        if (results.isEmpty()) return "Nessun documento trovato.";

        Document d0 = results.getFirst();
        System.out.println("[RAG] Primo documento:");
        System.out.println("  text.len=" + (d0.getText() == null ? 0 : d0.getText().length()));
        System.out.println("  metadata=" + d0.getMetadata());

        String section = String.valueOf(d0.getMetadata().get("section"));
        String date    = String.valueOf(d0.getMetadata().get("date"));
        String province= String.valueOf(d0.getMetadata().get("province"));

        System.out.println("[RAG] documento recuperato Sezione: " + section + ", Data: " + date + ", Provincia: " + province);



        // 4) Costruisci il contesto da passare al LLM
          String documents = results.stream()
                  .map(Document::getText)
                  .collect(java.util.stream.Collectors.joining("\n\n"));

          // (opzionale) aggiungi le "sorgenti" al prompt
          String sources = results.stream()
                  .map(doc -> formatSource(doc.getMetadata()))
                  .collect(java.util.stream.Collectors.joining("\n"));












        System.out.println("[RagService] Documenti recuperati: " + results.size());

          // 5) Prompt con template: input + contesto + sorgenti
          PromptTemplate promptTemplate = new PromptTemplate(getInfoTemplate);
        Message systemMessage = promptTemplate.createMessage(Map.of(
                "documents", documents,
                "sources", sources
        ));

        Message userMessage = new UserMessage(question);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));


        var resp = chatModel.call(prompt);
        return resp.getResult().getOutput().getText();
    }




    private void addInBatches(List<Document> docs, int batchSize) {
        if (docs == null || docs.isEmpty()) return;
        int n = docs.size();
        for (int i = 0; i < n; i += batchSize) {
            int end = Math.min(i + batchSize, n);
            vectorStore.add(docs.subList(i, end));
        }
    }

    // =========================
    // Ingest JSON strutturato
    // =========================
    public void ingestJSON(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            String rawDate = optText(root, "dataRiferimento");
            String date = normalizeDate(rawDate);
            String province = optText(root, "provincia").toUpperCase(Locale.ITALY);
            boolean partial = root.path("datiParziali").asBoolean(false);

            List<Document> docs = new ArrayList<>();

            // Header document
            Map<String, Object> hmd = new HashMap<>();
            hmd.put("contentType", "header");
            hmd.put("date", date);
            hmd.put("province", province);
            hmd.put("partial", partial);
            String headerText = "Report provinciale" + System.lineSeparator() +
                    "Provincia: " + province + System.lineSeparator() +
                    "Data di riferimento: " + date + System.lineSeparator() +
                    "Dati parziali: " + partial;
            docs.add(Document.builder().text(headerText).metadata(hmd).build());

            // Sezioni note: mappiamo chiave JSON -> nome sezione normalizzato
            Map<String, String> sections = new HashMap<>();
            sections.put("infoOrganicoView", "infoOrganico");
            sections.put("fattiDiRilievoView", "fattiDiRilievo");
            sections.put("denunciatiView", "denunciati");
            sections.put("arrestatiView", "arrestati");
            sections.put("pattuglieView", "pattuglie");
            sections.put("serviziView", "servizi");
            sections.put("immigrazioneView", "immigrazione");
            sections.put("controlliAmministrativiQuesturaView", "controlliAmministrativiQuestura");
            sections.put("reatiView", "reati");
            sections.put("misurePrevenzioneView", "misurePrevenzione");
            sections.put("sequestriQuesturaView", "sequestriQuestura");
            sections.put("attiviPrevenzTerritorioQuesturaView", "attiviPrevenzTerritorioQuestura");
            sections.put("attiviPrevenzUfficiInvestigativiQuesturaView", "attiviPrevenzUfficiInvestigativiQuestura");
            sections.put("attiviPrevenzAltriUfficiQuesturaView", "attiviPrevenzAltriUfficiQuestura");
            sections.put("attiviPrevenzCrimineView", "attiviPrevenzCrimine");

            for (var entry : sections.entrySet()) {
                JsonNode node = root.path(entry.getKey());
                if (!node.isMissingNode() && !node.isEmpty()) {
                    docs.add(sectionToDoc(entry.getValue(), node, date, province, partial));
                }
            }

            addInBatches(docs, 200);
        } catch (Exception e) {
            throw new RuntimeException("Errore durante l'ingest del JSON", e);
        }
    }

    private Document sectionToDoc(String sectionName, JsonNode node, String date, String province, boolean partial) {
        Map<String, Object> md = new HashMap<>();
        md.put("contentType", "section");
        md.put("section", sectionName);
        md.put("date", date);
        md.put("province", province);
        md.put("partial", partial);

        StringBuilder sb = new StringBuilder("## ").append(sectionName).append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("| metrica | valore |").append(System.lineSeparator())
                .append("|---|---:|").append(System.lineSeparator());
        node.fieldNames().forEachRemaining(f -> {
            JsonNode v = node.get(f);
            String val = v == null ? "" : (v.isNumber() ? v.numberValue().toString() : v.asText(""));
            sb.append("|").append(f).append("|").append(val).append("|").append(System.lineSeparator());
        });
        return Document.builder().text(sb.toString()).metadata(md).build();
    }

    private String optText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() ? "" : v.asText("");
    }

    private String normalizeDate(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (var f : fmts) {
            try {
                return LocalDate.parse(raw.trim(), f).toString();
            } catch (Exception ignored) {
            }
        }
        return raw.trim();
    }

    // Estrae dal testo della sezione (tabella markdown) le coppie metrica->valore
    private Map<String, String> parseSectionMetrics(String text) {
        Map<String, String> out = new HashMap<>();
        if (text == null || text.isBlank()) return out;
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            String ln = line.trim();
            // righe attese: | metrica | valore |
            if (ln.startsWith("|") && ln.endsWith("|") && !ln.startsWith("|---")) {
                String[] parts = ln.split("\\|");
                // parts: ["", " metrica ", " valore ", ""]
                if (parts.length >= 3) {
                    String key = parts[1].trim();
                    String val = parts[2].trim();
                    if (!key.isEmpty() && !val.isEmpty() && !key.equalsIgnoreCase("metrica") && !key.equalsIgnoreCase("valore")) {
                        out.put(key, val);
                    }
                }
            }
        }
        System.out.println("[RagService] parseSectionMetrics -> estratte " + out.size() + " metriche.");
        return out;
    }

    // =========================
    // Parsing filtri dalla domanda (italiano)
    // =========================
    private static class ParsedFilters {
        String province;
        String dateIso; // yyyy-MM-dd
        String section;
        Boolean partial;
    }

    private ParsedFilters parseFiltersFromQuestion(String question) {
        ParsedFilters pf = new ParsedFilters();
        if (question == null) return pf;
        String q = question.trim();
        String qLower = q.toLowerCase(Locale.ITALY);

        // 1) Date naturali: oggi/ieri
        LocalDate today = LocalDate.now();
        if (qLower.contains("oggi")) {
            pf.dateIso = today.toString();
        } else if (qLower.contains("ieri")) {
            pf.dateIso = today.minusDays(1).toString();
        }

        // 2) Date esplicite: gg/mm/aaaa oppure gg-mm-aaaa
        if (pf.dateIso == null) {
            Pattern pDate = Pattern.compile("(\\b[0-3]?\\d)[/\\-]([0-1]?\\d)[/\\-]((?:19|20)?\\d{2})");
            Matcher m = pDate.matcher(q);
            if (m.find()) {
                String dd = String.format("%02d", Integer.parseInt(m.group(1)));
                String MM = String.format("%02d", Integer.parseInt(m.group(2)));
                String yyyy = m.group(3);
                if (yyyy.length() == 2) {
                    // euristica: anni 00..79 -> 2000..2079, 80..99 -> 1980..1999
                    int yy = Integer.parseInt(yyyy);
                    yyyy = (yy < 80 ? 2000 + yy : 1900 + yy) + "";
                }
                pf.dateIso = LocalDate.parse(dd + "/" + MM + "/" + yyyy, DateTimeFormatter.ofPattern("dd/MM/yyyy")).toString();
            }
        }

        // 3) Sezione: mapping di sinonimi -> chiavi section
        Map<String, String> sectionSyn = new HashMap<>();
        sectionSyn.put("arrestati", "arrestati");
        sectionSyn.put("arresti", "arrestati");
        sectionSyn.put("denunciati", "denunciati");
        sectionSyn.put("denunce", "denunciati");
        sectionSyn.put("pattuglie", "pattuglie");
        sectionSyn.put("servizi", "servizi");
        sectionSyn.put("immigrazione", "immigrazione");
        sectionSyn.put("controlli amministrativi", "controlliAmministrativiQuestura");
        sectionSyn.put("reati", "reati");
        sectionSyn.put("misure di prevenzione", "misurePrevenzione");
        sectionSyn.put("misure prevenzione", "misurePrevenzione");
        sectionSyn.put("sequestri", "sequestriQuestura");
        sectionSyn.put("prevenzione territorio", "attiviPrevenzTerritorioQuestura");
        sectionSyn.put("prevenzione investigativi", "attiviPrevenzUfficiInvestigativiQuestura");
        sectionSyn.put("prevenzione altri uffici", "attiviPrevenzAltriUfficiQuestura");
        sectionSyn.put("crimine", "attiviPrevenzCrimine");

        for (var e : sectionSyn.entrySet()) {
            if (qLower.contains(e.getKey())) {
                pf.section = e.getValue();
                break;
            }
        }

        // 4) Provincia: usa dizionario completo (nomi estesi, sigle, articoli/apostrofi, forme istituzionali)
        try {
            ProvinceDictionary.MatchResult mr = provinceDictionary.matchProvince(q);
            if (mr != null) {
                if (mr.ambiguous) {
                    System.out.println("[RagService] Provincia ambigua nella domanda. Candidati: " + (mr.candidates == null ? "[]" : mr.candidates));
                    // Non impostiamo il filtro provincia per massimizzare il recall; la risposta inviterà alla disambiguazione se necessario
                } else if (mr.canonicalName != null) {
                    pf.province = mr.canonicalName; // già MAIUSCOLO
                }
            }
        } catch (Throwable t) {
            // In caso di problemi col dizionario, non bloccare la ricerca; mantieni il comportamento precedente (nessun filtro)
            System.out.println("[RagService] Errore nel matching provincia: " + t.getMessage());
        }

        // 5) Dati parziali
        if (qLower.contains("parzial")) {
            pf.partial = true;
        } else if (qLower.contains("completo") || qLower.contains("completi")) {
            pf.partial = false;
        }

        return pf;
    }
}
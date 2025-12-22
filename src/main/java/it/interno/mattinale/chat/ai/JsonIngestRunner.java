
package it.interno.mattinale.chat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.interno.mattinale.chat.ai.util.JsonConverters;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class JsonIngestRunner implements CommandLineRunner {

    private final VectorStore vectorStore;

    @Value("classpath:questuraIns-2025-11-01.json")
    Resource questuraInsResouce1;

    @Value("classpath:questuraIns-2025-11-02.json")
    Resource questuraInsResouce2;

    @Value("classpath:coscIns-2025-12-14.json")
    Resource coscInsResouce2;

    @Value("classpath:coscIns-2025-12-13.json")
    Resource coscInsResouce1;

    private final JsonConverters jsonConverters;

    public JsonIngestRunner(VectorStore vectorStore, JsonConverters jsonConverters) {
        this.vectorStore = vectorStore;
        this.jsonConverters = jsonConverters;
    }


    private void addjsonQuestura(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream())) {
            List<Document> docs = readQuesturaReportAsSectionDocuments(reader);

            // (opzionale) split se qualche sezione fosse lunga:
            var splitter = new org.springframework.ai.transformer.splitter.TokenTextSplitter();
            List<Document> chunks = splitter.split(docs);

            vectorStore.add(chunks);
            System.out.println("[JsonIngestRunner] Ingest Questura OK: " + chunks.size() + " chunks from " + resource.getFilename());
        } catch (Exception e) {
            System.out.println("[JsonIngestRunner] Errore durante l'ingest del JSON Questura : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addjsonCosc(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream())) {
            List<Document> docs = readCoscReportAsSectionDocuments(reader);

            // (opzionale) split se qualche sezione fosse lunga:
            var splitter = new org.springframework.ai.transformer.splitter.TokenTextSplitter();
            List<Document> chunks = splitter.split(docs);

            vectorStore.add(chunks);
            System.out.println("[JsonIngestRunner] Ingest COSC OK: " + chunks.size() + " chunks from " + resource.getFilename());
        } catch (Exception e) {
            System.out.println("[JsonIngestRunner] Errore durante l'ingest del JSON COSC: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void run(String... args) {
        addjsonQuestura(questuraInsResouce1);
        addjsonQuestura(questuraInsResouce2);
        addjsonCosc(coscInsResouce1);
        addjsonCosc(coscInsResouce2);
    }

    private List<Document> readQuesturaReportAsSectionDocuments(Reader reader) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(reader);

        String provincia = text(root, "provincia");           // "ROMA"
        Boolean partial = bool(root, "datiParziali");         // true/false
        String isoDate = toIso(text(root, "dataRiferimento")); // "2025-11-02"

        Map<String, String> sectionToField = new LinkedHashMap<>();
        //SEZIONE QUESTURA
        sectionToField.put("organico", "infoOrganicoView");
        sectionToField.put("fattiDiRilievo", "fattiDiRilievoView");
        sectionToField.put("denunciati", "denunciatiView");
        sectionToField.put("arrestati", "arrestatiView");

        sectionToField.put("pattuglie", "pattuglieView");
        sectionToField.put("servizi", "serviziView");
        sectionToField.put("immigrazione", "immigrazioneView");
        sectionToField.put("controlliAmministrativiQuestura", "controlliAmministrativiQuesturaView");
        sectionToField.put("reati", "reatiView");
        sectionToField.put("misurePrevenzione", "misurePrevenzioneView");
        sectionToField.put("sequestriQuestura", "sequestriQuesturaView");
        sectionToField.put("attiviPrevenzTerritorioQuestura", "attiviPrevenzTerritorioQuesturaView");
        sectionToField.put("attiviPrevenzUfficiInvestigativiQuestura", "attiviPrevenzUfficiInvestigativiQuesturaView");
        sectionToField.put("attiviPrevenzAltriUfficiQuestura", "attiviPrevenzAltriUfficiQuesturaView");
        sectionToField.put("attiviPrevenzCrimine", "attiviPrevenzCrimineView");

       List<Document> docs = new ArrayList<>();
        for (var entry : sectionToField.entrySet()) {
            String sectionName = entry.getKey();
            String jsonField = entry.getValue();
            JsonNode node = root.get(jsonField);
            if (node == null || node.isNull()) continue;

            String content = makeReadableContent(node);
           // System.out.println("Section " + sectionName + " content length: " + content.length() + "content: " + content);

            Map<String, Object> md = new LinkedHashMap<>();
            md.put("date", isoDate);
            md.put("province", provincia);
            md.put("partial", partial != null ? partial : false);
            md.put("contentType", "section");
            md.put("section", sectionName);
            md.put("source", "questura");

            docs.add(new Document(content, md));
        }
        return docs;
    }



    private List<Document> readCoscReportAsSectionDocuments(Reader reader) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(reader);

        String provincia = text(root, "provincia");           // "ROMA"
        Boolean partial = bool(root, "datiParziali");         // true/false
        String isoDate = toIso(text(root, "dataRiferimento")); // "2025-11-02"

        Map<String, String> sectionToField = new LinkedHashMap<>();
        //SEZIONE QUESTURA
        sectionToField.put("organico", "infoOrganicoView");
        sectionToField.put("fattiDiRilievo", "fattiDiRilievoView");
        sectionToField.put("denunciati", "denunciatiView");
        sectionToField.put("arrestati", "arrestatiView");
        sectionToField.put("perquisizioni", "perquisizioniView");
        sectionToField.put("monitoraggioWeb", "monitoraggioWebView");
        sectionToField.put("oscuramentoWeb", "oscuramentoWebView");
        sectionToField.put("noscWeb", "noscWebView");
        sectionToField.put("crimineEconFinanOnLine", "crimineEconFinanOnLineView");
        sectionToField.put("attiviPrevenzTerritorioCosc", "attiviPrevenzTerritorioCoscView");


        List<Document> docs = new ArrayList<>();
        for (var entry : sectionToField.entrySet()) {
            String sectionName = entry.getKey();
            String jsonField = entry.getValue();
            JsonNode node = root.get(jsonField);
            if (node == null || node.isNull()) continue;

            String content = makeReadableContent(node);
            //System.out.println("Section " + sectionName + " content length: " + content.length() + "content: " + content);
            Map<String, Object> md = new LinkedHashMap<>();
            md.put("date", isoDate);
            md.put("province", provincia);
            md.put("partial", partial != null ? partial : false);
            md.put("contentType", "section");
            md.put("section", sectionName);
            md.put("source", "cosc");

            docs.add(new Document(content, md));
        }
        return docs;
    }



    private String makeReadableContent(JsonNode node) {
        return jsonConverters.toJson(node);
    }

    private String text(JsonNode node, String field) {
        return (node.has(field) && !node.get(field).isNull()) ? node.get(field).asText() : null;
    }
    private Boolean bool(JsonNode node, String field) {
        return (node.has(field) && node.get(field).isBoolean()) ? node.get(field).asBoolean() : null;
    }

    // "02/11/2025" -> "2025-11-02"
    private String toIso(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.isBlank()) return null;
        try {
            var in = new SimpleDateFormat("dd/MM/yyyy");
            in.setLenient(false);
            var out = new SimpleDateFormat("yyyy-MM-dd");
            return out.format(in.parse(ddMMyyyy));
        } catch (Exception e) {
            return ddMMyyyy; // fallback
        }
    }
}

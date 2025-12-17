package it.interno.mattinale.chat.ai;

import it.interno.mattinale.chat.ai.model.ChatRequest;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("rag")
public class RagController {

    private final RagService ragService;



    @Autowired
    public RagController(RagService ragService) {
        this.ragService = ragService;

    }

    @GetMapping("/ping")
    public String ping() {
        System.out.println("Ping!");
        System.out.println("response pong!!");
        return "Pong!";
    }




    @PostMapping(value = "/uploadPdf", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            // Save the uploaded file to the file system
            File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
            file.transferTo(convFile);
            ragService.ingestPDF(new FileSystemResource(convFile));

            return ResponseEntity.ok().body("File caricato e processato!");
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return ResponseEntity.internalServerError().body("Errore nel caricamento e nel processamento del file");
        }
    }






    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> generateRAGAnswer(
            @Valid @RequestBody ChatRequest chatRequest
    ) {
        // I filtri vengono dedotti automaticamente dal testo della domanda
        return ResponseEntity.ok(ragService.generateAnswer(chatRequest.getText()));
    }

    @PostMapping(value = "/uploadJson", consumes = "application/json")
    public ResponseEntity<String> uploadJson(@RequestBody String jsonBody) {
        try {
            ragService.ingestJSON(jsonBody);
            return ResponseEntity.ok("JSON indicizzato correttamente");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Errore durante l'ingest del JSON: " + e.getMessage());
        }
    }

    // Endpoint per ingest bulk da una cartella (senza abilitare il runner)
    @PostMapping("/ingestDaily")
    public ResponseEntity<String> ingestDaily(@RequestParam(value = "dir", required = false) String dirPath) {
        String usedDir = (dirPath == null || dirPath.isBlank())
                ? "src/main/resources/daily/2025-11"
                : dirPath;
        Path dir = Paths.get(usedDir);
        if (!Files.exists(dir)) {
            return ResponseEntity.badRequest().body("Directory non trovata: " + usedDir);
        }
        int success = 0;
        int errors = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                try {
                    String json = Files.readString(p);
                    if (json != null && !json.isBlank()) {
                        ragService.ingestJSON(json);
                        success++;
                    }
                } catch (Exception ex) {
                    errors++;
                }
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Errore lettura directory: " + e.getMessage());
        }
        return ResponseEntity.ok("Ingest completato. File OK=" + success + ", errori=" + errors + ", dir=" + usedDir);
    }






}

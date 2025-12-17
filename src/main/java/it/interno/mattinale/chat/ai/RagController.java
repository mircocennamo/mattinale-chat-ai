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
 @PostMapping("/ask")
    public ResponseEntity<ChatResponse> generateRAGAnswer(
            @Valid @RequestBody ChatRequest chatRequest
    ) {
        // I filtri vengono dedotti automaticamente dal testo della domanda
        return ResponseEntity.ok(ragService.generateAnswer(chatRequest.getText()));
    }

}

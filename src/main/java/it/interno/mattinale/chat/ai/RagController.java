package it.interno.mattinale.chat.ai;

import it.interno.mattinale.chat.ai.model.ChatRequest;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import it.interno.mattinale.chat.ai.service.ChatOrchestrator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("rag")
public class RagController {

    private final ChatOrchestrator chatOrchestrator;

    @Autowired
    public RagController(ChatOrchestrator chatOrchestrator) {
        this.chatOrchestrator = chatOrchestrator;

    }

    @GetMapping("/ping")
    public String ping() {
        System.out.println("Ping!");
        System.out.println("response pong!!");
        return "Pong!";
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> generateRAGAnswer(
            @Valid @RequestBody ChatRequest chatRequest) throws Exception {
        // I filtri vengono dedotti automaticamente dal testo della domanda
        return ResponseEntity.ok(chatOrchestrator.handleQuery(chatRequest.getText(), chatRequest.getSession_id()));
    }

}

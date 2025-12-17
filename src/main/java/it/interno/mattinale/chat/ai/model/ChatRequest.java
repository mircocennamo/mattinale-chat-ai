package it.interno.mattinale.chat.ai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ChatRequest {

    @NotBlank
    private String text;

    // ISO 8601 molto permissivo (puoi renderlo più rigoroso o usare Instant)
    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T.*Z?$", message = "timestamp deve essere ISO-8601")
    private String timestamp;

    @NotBlank
    private String session_id;

    // getter/setter
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getSession_id() { return session_id; }
    public void setSession_id(String session_id) { this.session_id = session_id; }
}


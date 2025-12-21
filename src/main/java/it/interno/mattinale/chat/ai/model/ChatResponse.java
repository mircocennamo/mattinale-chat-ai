package it.interno.mattinale.chat.ai.model;

public class ChatResponse {
    private String text;
    private String receivedAt; // ISO 8601 lato server
    private String image; // opzionale: data:image/...;base64,...

    public ChatResponse() {
    }

    public ChatResponse(String text, String receivedAt, String image) {
        this.text = text;
        this.receivedAt = receivedAt;
        this.image = image;
    }

    public String getText() {
        return text;
    }

    public String getImage() {
        return image;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }

    private java.util.List<Suggestion> suggestions;

    public java.util.List<Suggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(java.util.List<Suggestion> suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    public String toString() {
        return "ChatResponse{" +
                "text='" + text + '\'' +
                ", receivedAt='" + receivedAt + '\'' +
                ", image='" + image + '\'' +
                ", suggestions=" + suggestions +
                '}';
    }
}

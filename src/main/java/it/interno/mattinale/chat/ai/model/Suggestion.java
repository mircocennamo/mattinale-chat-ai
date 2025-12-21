package it.interno.mattinale.chat.ai.model;

public class Suggestion {
    private String text;
    private String action;
    private String paramName;

    public Suggestion() {
    }

    public Suggestion(String text, String action, String paramName) {
        this.text = text;
        this.action = action;
        this.paramName = paramName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    @Override
    public String toString() {
        return "Suggestion{" +
                "text='" + text + '\'' +
                ", action='" + action + '\'' +
                ", paramName='" + paramName + '\'' +
                '}';
    }
}

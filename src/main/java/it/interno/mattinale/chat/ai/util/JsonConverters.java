package it.interno.mattinale.chat.ai.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
public class JsonConverters {

    private final ObjectMapper mapper; // Inietta il bean configurato da Spring Boot

    public JsonConverters(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ChatResponse toChatResponse(String json) throws JsonProcessingException {
        return mapper.readValue(json, ChatResponse.class);
    }

    public String getJsonSchema() {
        try {
            JsonSchemaGenerator schemaGenerator= new JsonSchemaGenerator(mapper);
            JsonSchema jsonSchema =  schemaGenerator.generateSchema(ChatResponse.class);

// Serializza in stringa
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Errore durante la generazione dello schema JSON", e);
        }
    }
}


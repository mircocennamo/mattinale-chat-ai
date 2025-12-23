package it.interno.mattinale.chat.ai.util;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JsonConverters {

    private final ObjectMapper mapper; // Inietta il bean configurato da Spring Boot

    public JsonConverters(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ChatResponse toChatResponse(String json) throws JsonProcessingException {
        return mapper.readValue(json, ChatResponse.class);
    }
    public String toJson(Object obj)  {
        try
        {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Errore durante la serializzazione in JSON", e);
        }
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

    public String toJson(List<Map<String,Object>> obj) throws JsonProcessingException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }


    /**
     * Restituisce il JsonNode serializzato come stringa JSON.
     *
     * @param node il JsonNode da serializzare
     * @return la rappresentazione JSON del nodo
     */
    public  String toJson(JsonNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Errore nella serializzazione del JsonNode", e);
        }
    }

}


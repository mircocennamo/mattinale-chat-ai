package demo.elastic.rag.conf;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.oracle.OracleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;


@Configuration
public class FunctionCallingChatConfiguration {



    @Bean
    public ChatClient ragChatClient(ChatModel chatModel, VectorStore vectorStore) {
        var qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .build();

        // ChatClient con RAG "always-on"
        return ChatClient.builder(chatModel)
                .defaultAdvisors(qaAdvisor)
                .build();
    }







    @Bean
    OracleVectorStore oracleVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return OracleVectorStore.builder(jdbcTemplate, embeddingModel)
                .tableName("AI_VECTOR_STORE")                 // tabella con colonna VECTOR
                .distanceType(OracleVectorStore.OracleVectorStoreDistanceType.COSINE)
                .indexType(OracleVectorStore.OracleVectorStoreIndexType.IVF)
                .dimensions(1536)
                .forcedNormalization(true)         // <<< normalizzazione lato client
                .initializeSchema(true)            // crea schema se necessario
                .build();
    }

}





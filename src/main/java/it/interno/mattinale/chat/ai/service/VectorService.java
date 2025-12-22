package it.interno.mattinale.chat.ai.service;

import it.interno.mattinale.chat.ai.enumeration.UserIntent;
import it.interno.mattinale.chat.ai.model.QueryPlan;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorService {
    private final VectorStore vectorStore;


    public VectorService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> search(QueryPlan queryPlan) {
        if(queryPlan == null || !queryPlan.requiresVector() || UserIntent.CAPABILITIES.equals(queryPlan.userIntent())) {
            return List.of();
        }
        String filterExpression = buildFilter(queryPlan);
        System.out.println("Filter Expression: " + filterExpression);
        //if (!queryPlan.requiresVector())
       //     return List.of();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("*")                                 // ⚠️ necessario!
                        .filterExpression(filterExpression)         // es.: "province == 'ROMA' AND section == 'arrestati'"
                        .topK(100)                                  // recupera più contesto
                        // .similarityThreshold(0.70)               // opzionale: riduci se il dominio è vario
                        .build()
        );

    }

    private String buildFilter(QueryPlan queryPlan) {
        StringBuilder filter = new StringBuilder();
        if (queryPlan.province() != null) {
            filter.append("province == '").append(queryPlan.province()).append("' ");
        }
        if (queryPlan.section() != null) {
            if (!filter.isEmpty()) filter.append("AND ");
            filter.append("section == '").append(queryPlan.section()).append("' ");
        }
        if (queryPlan.dateRange() != null) {
            if (!filter.isEmpty()) filter.append("AND ");
            // Esempio di gestione di un singolo intervallo di date
            filter.append("date >= '").append(queryPlan.dateRange().from()).append("' ");
            filter.append("AND date <= '").append(queryPlan.dateRange().to()).append("' ");
        }
        if (queryPlan.source() != null) {
            if (!filter.isEmpty()) filter.append("AND ");
            filter.append("source == '").append(queryPlan.source()).append("' ");
        }
        /*if (queryPlan.partial() != null) {
            if (!filter.isEmpty()) filter.append("AND ");
            filter.append("partial == ").append(queryPlan.partial()).append(" ");
        }
        if (queryPlan.contentType() != null) {
            if (!filter.isEmpty()) filter.append("AND ");
            filter.append("contentType == '").append(queryPlan.contentType()).append("' ");
        }
        */



        return filter.toString().trim();
    }
}

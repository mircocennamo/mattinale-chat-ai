package it.interno.mattinale.chat.ai.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.interno.mattinale.chat.ai.component.Oracle23AiTools;
import it.interno.mattinale.chat.ai.model.ChatResponse;
import it.interno.mattinale.chat.ai.model.QueryPlan;
import it.interno.mattinale.chat.ai.util.ToolContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static it.interno.mattinale.chat.ai.enumeration.UserIntent.*;

@Service
public class RoutingAgent {

    private final QueryPlannerService planner;
    private final Oracle23AiTools tools;
    private final VectorService vectorService;
    private final AnswerService answerService; // già nel tuo progetto
    private final ChatModel chatModel;
    private final ObjectMapper mapper = new ObjectMapper();// se vuoi function-calling

    public RoutingAgent(
            QueryPlannerService planner,
            Oracle23AiTools tools,
            VectorService vectorService,
            AnswerService answerService,
            ChatModel chatModel) {
        this.planner = planner;
        this.tools = tools;
        this.vectorService = vectorService;
        this.answerService = answerService;
        this.chatModel = chatModel;
    }

    public ChatResponse route(String question, String conversationId) throws com.fasterxml.jackson.core.JsonProcessingException {
        // 1) Pianifica
        QueryPlan plan = planner.plan(question, conversationId);

        // 2) Costruisci contesto tool
        Map<String,Object> ctxMap = ToolContext.from(plan);
        var toolCtx = new org.springframework.ai.chat.model.ToolContext(ctxMap);

        // 3) Se serve Vector Retrieval
        List<Document> docs;
        if (Boolean.TRUE.equals(plan.requiresVector())) {
            docs = vectorService.search(plan); // usa FilterExpressionBuilder + topK
        } else {
            docs = new ArrayList<>();
        }

        // 4) Routing deterministico
        switch (plan.userIntent()) {
            case TREND -> {
                var province = (String) ctxMap.get("province");
                var section  = (String) ctxMap.get("section");
                var source   = (String) ctxMap.get("source");
                var date1    = (java.time.LocalDate) ctxMap.get("date1");
                var date2    = (java.time.LocalDate) ctxMap.get("date2");
                var trend = tools.getTemporalTrend(toolCtx, province, section, source, date1, date2, (int) ctxMap.get("days"));
                trend.forEach(t->docs.add(toDocument(t)));
                return answerService.generate(plan, question, docs,TREND);
            }
            case COMPARE -> {
                var province = (String) ctxMap.get("province");
                var section  = (String) ctxMap.get("section");
                var date1    = (java.time.LocalDate) ctxMap.get("date1");
                var date2    = (java.time.LocalDate) ctxMap.get("date2");
                var cmp = tools.compareTwoDates(toolCtx, date1, date2, province, section);
                cmp.forEach(t->docs.add(toDocument(t)));
                return answerService.generate(plan, question, docs,COMPARE);
            }
            case COUNT -> {
                var province = (String) ctxMap.get("province");
                var section  = (String) ctxMap.get("section");
                var source   = (String) ctxMap.get("source");
                var date1    = (java.time.LocalDate) ctxMap.get("date1");
                var date2    = (java.time.LocalDate) ctxMap.get("date2");
                int count = tools.countByFilter(toolCtx, province, section, date1, date2, source);
                docs.add(toDocument(count));
                return answerService.generate(plan, question, docs,COUNT);

            }
            case MIN_MAX -> {
                var province = (String) ctxMap.get("province");
                var section  = (String) ctxMap.get("section");
                var source   = (String) ctxMap.get("source");
                var date1    = (java.time.LocalDate) ctxMap.get("date1");
                var date2    = (java.time.LocalDate) ctxMap.get("date2");
                var mm = tools.getMinMaxByPeriod(toolCtx, province, section, date1, date2, (int) ctxMap.get("days"), source);
                mm.forEach(t->docs.add(toDocument(t)));
                return answerService.generate(plan, question, docs,MIN_MAX);
             }
            case DETAIL -> {
                // Drill-down: contenuti
                var province = (String) ctxMap.get("province");
                var section  = (String) ctxMap.get("section");
                var source   = (String) ctxMap.get("source");
                var date1    = (java.time.LocalDate) ctxMap.get("date1");
                var date2    = (java.time.LocalDate) ctxMap.get("date2");
                var list = tools.getDocumentsByFilter(toolCtx, province, section, source, date1, date2, (int) ctxMap.get("days"));
                list.forEach(t->docs.add(toDocument(t)));
                return answerService.generate(plan, question, docs,DETAIL);

            }
            case FULL_ARTICLE -> {
                // Drill-down: contenuti
                var province = (String) ctxMap.get("province");
                var source   = (String) ctxMap.get("source");
                var date1    = (java.time.LocalDate) ctxMap.get("date1");
                var date2    = (java.time.LocalDate) ctxMap.get("date2");
                var list = tools.getFullArticleByFilter(toolCtx, province, source, date1, date2, (int) ctxMap.get("days"));
                list.forEach(t->docs.add(toDocument(t)));
                return answerService.generate(plan, question, docs,FULL_ARTICLE);
             }
            case CAPABILITIES -> {
                return answerService.generate(plan, question, docs,CAPABILITIES);
            }
            default -> {
                // RAG generico (senza tool) con docs/contesto
                return answerService.generate(plan, question, docs,null);
            }
        }
    }


    public  Document toDocument(Object obj)  {
        try
        {
            mapper.findAndRegisterModules();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            String json =  mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            return new Document(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Errore durante la serializzazione in JSON", e);
        }
    }
}


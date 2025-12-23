package it.interno.mattinale.chat.ai.util;

import it.interno.mattinale.chat.ai.enumeration.UserIntent;
import it.interno.mattinale.chat.ai.model.DataRange;
import it.interno.mattinale.chat.ai.model.QueryPlan;

import java.util.Map;
import java.util.List;

public class ToolsMapping {

    public static final Map<UserIntent, List<String>> INTENT_TO_TOOLS = Map.of(
            UserIntent.DETAIL,
            List.of("getDocumentsByFilter", "getOnlyFinalData", "aggregateBySection", "aggregateBySource"),
            UserIntent.COUNT, List.of("countByFilter"),
            UserIntent.TREND, List.of("getTemporalTrend", "barChart"),
            UserIntent.COMPARE, List.of("compareTwoDates"),
            UserIntent.MIN_MAX, List.of("getMinMaxByPeriod"),
            UserIntent.CHART, List.of("getChartDataset","barChart"),
            UserIntent.CAPABILITIES, List.of(),
            UserIntent.SEARCH,
            List.of("getDocumentsByFilter", "getOnlyFinalData", "aggregateBySection", "aggregateBySource"),
            UserIntent.DISCOVERY,List.of("getDistinctMetadata"),
            UserIntent.FULL_ARTICLE, List.of("getFullArticleByFilter"));

    private ToolsMapping() {
        // Prevent instantiation
    }

    public static Map<String,Object> getTools(QueryPlan queryPlan){
        if(queryPlan==null){
            return Map.of();
        }
        final var provincia = queryPlan.province() == null ? "" : queryPlan.province();

        final var sezione = queryPlan.section() == null ? "" : queryPlan.section();

        final var sorgente = queryPlan.source() == null ? "" : queryPlan.source();

        if(queryPlan.dateRange()==null){
           return Map.of(
                "province", provincia,
                "section", sezione,
                "source", sorgente,
                "date",    DataRange.defaultRange().from(),
                "date1",   DataRange.defaultRange().from(),
                "date2",   DataRange.defaultRange().to(),
                "days",    DataRange.defaultRange().days());

    }else{
            return Map.of(
                "province", provincia,
                "section", sezione,
                "source",  sorgente,
                "date",    queryPlan.dateRange().from(),
                "date1",   queryPlan.dateRange().from(),
                "date2",   queryPlan.dateRange().to(),
                "days",    queryPlan.dateRange().days());
        }
    }
}






//    GENERIC
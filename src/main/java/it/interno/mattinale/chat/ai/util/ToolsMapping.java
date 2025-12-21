package it.interno.mattinale.chat.ai.util;

import it.interno.mattinale.chat.ai.enumeration.UserIntent;
import it.interno.mattinale.chat.ai.model.QueryPlan;

import java.util.Map;
import java.util.List;

public class ToolsMapping {

    public static final Map<UserIntent, List<String>> INTENT_TO_TOOLS = Map.of(
            UserIntent.DETAIL,
            List.of("getDocumentsByFilter", "getOnlyFinalData", "aggregateBySection", "aggregateBySource"),
            UserIntent.COUNT, List.of("countByFilter"),
            UserIntent.TREND, List.of("getTemporalTrend", "getChartDataset"),
            UserIntent.COMPARE, List.of("compareTwoDates"),
            UserIntent.MIN_MAX, List.of("getMinMaxByPeriod"),
            UserIntent.CHART, List.of("barChart", "getChartDataset"));

    private ToolsMapping() {
        // Prevent instantiation
    }

    public static Map<String,Object> getTools(QueryPlan queryPlan){
        return Map.of(
                "province", queryPlan.province()==null?"":queryPlan.province(),
                "section", queryPlan.section()==null?"":queryPlan.section(),
                "source", queryPlan.source()==null?"":queryPlan.source(),
                "date", queryPlan.dateRange().from()==null?null:queryPlan.dateRange().from(),
                "date1", queryPlan.dateRange().from()==null?null:queryPlan.dateRange().from(),
                "date2", queryPlan.dateRange().to()==null?null:queryPlan.dateRange().to(),
                "days", queryPlan.dateRange().days()==null?0:queryPlan.dateRange().days());

    }
}






//    GENERIC
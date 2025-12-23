package it.interno.mattinale.chat.ai.util;


import it.interno.mattinale.chat.ai.model.QueryPlan;

import java.util.Map;

public final class ToolContext {
    private ToolContext() {}

    public static Map<String, Object> from(QueryPlan qp) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("province", qp.province() == null ? "" : qp.province());
        m.put("section",  qp.section()  == null ? "" : qp.section());
        m.put("source",   qp.source()   == null ? "" : qp.source());

        if (qp.dateRange() != null) {
            // Assumi ISO yyyy-MM-dd (coerente con le query SQL)
            m.put("date",  qp.dateRange().from());
            m.put("date1", qp.dateRange().from());
            m.put("date2", qp.dateRange().to());
            m.put("days",  qp.dateRange().days() == null ? 0 : qp.dateRange().days());
        } else {
            m.put("date",  null);
            m.put("date1", null);
            m.put("date2", null);
            m.put("days",  0);
        }
        return m;
    }
}


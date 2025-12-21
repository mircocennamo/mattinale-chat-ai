package it.interno.mattinale.chat.ai.model;

import it.interno.mattinale.chat.ai.enumeration.UserIntent;

public record QueryPlan(
        Boolean requiresSql,
        Boolean requiresVector,
        UserIntent userIntent,
        DataRange dateRange,
        String section,
        String source,
        String province,
        Boolean partial,
        String contentType
)

{
}



//{"date":"2025-11-02","section":"attiviPrevenzUfficiInvestigativi",
// "source":"questura","province":"ROMA","partial":true,"contentType":"section"}
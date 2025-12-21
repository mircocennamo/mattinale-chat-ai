package it.interno.mattinale.chat.ai.component;



import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

@Component
public class Oracle23AiTools {

    private final JdbcTemplate jdbcTemplate;

    public Oracle23AiTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /* ============================================================
       1. Estrazione contenuti filtrati (drill-down)
       ============================================================ */
    @Tool(
            name = "getDocumentsByFilter",
            description = "Recupera i contenuti filtrati per data, provincia, sezione e source"
    )
    public List<DocumentDto> getDocumentsByFilter(ToolContext toolContext,
                                                  LocalDate date,
                                                  String province,
                                                  String section,
                                                  String source
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            date =(LocalDate) toolContext.getContext().get("date");
            province = (String)toolContext.getContext().get("province");
            section = (String)toolContext.getContext().get("section");
            source = (String)toolContext.getContext().get("source");

        }
        return jdbcTemplate.query("""
        SELECT content, metadata
        FROM AI_VECTOR_STORE
        WHERE json_value(metadata,'$.date') = ?
          AND json_value(metadata,'$.province') = ?
          AND json_value(metadata,'$.section') = ?
          AND json_value(metadata,'$.source') = ?
    """, DocumentDto.ROW_MAPPER,
                date.toString(), province, section, source);
    }

    /* ============================================================
       2. Conteggio semplice
       ============================================================ */
    @Tool(
            name = "countByFilter",
            description = "Conta i documenti in base ai metadata"
    )
    public int countByFilter(ToolContext toolContext,
            LocalDate date,
            String province,
            String section
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            date =(LocalDate) toolContext.getContext().get("date");
            province = (String)toolContext.getContext().get("province");
            section = (String)toolContext.getContext().get("section");
        }
        return jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM AI_VECTOR_STORE
        WHERE json_value(metadata,'$.date') = ?
          AND json_value(metadata,'$.province') = ?
          AND json_value(metadata,'$.section') = ?
    """, Integer.class,
                date.toString(), province, section);
    }

    /* ============================================================
       3. Andamento temporale (trend)
       ============================================================ */
    @Tool(
            name = "getTemporalTrend",
            description = "Restituisce l'andamento temporale aggregato"
    )
    public List<TrendPointDto> getTemporalTrend(ToolContext toolContext,
            String province,
            String section,
            int days
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            province = (String)toolContext.getContext().get("province");
            section = (String)toolContext.getContext().get("section");
            days =(int) toolContext.getContext().get("days");
        }
        return jdbcTemplate.query("""
        SELECT
          json_value(metadata,'$.date') AS day,
          COUNT(*) AS total
        FROM AI_VECTOR_STORE
        WHERE json_value(metadata,'$.province') = ?
          AND json_value(metadata,'$.section') = ?
          AND json_value(metadata,'$.date') >= TRUNC(SYSDATE) - ?
        GROUP BY json_value(metadata,'$.date')
        ORDER BY day
    """, TrendPointDto.ROW_MAPPER,
                province, section, days);
    }

    /* ============================================================
       4. Confronto due date
       ============================================================ */
    @Tool(
            name = "compareTwoDates",
            description = "Confronta i dettagli e il contenuto dei documenti tra due date per sezione e provincia"
    )
    public List<ComparisonPointDto> compareTwoDates(ToolContext toolContext,
            LocalDate date1,
            LocalDate date2,
            String province,
            String section
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            date1 =(LocalDate) toolContext.getContext().get("date1");
            date2 =(LocalDate) toolContext.getContext().get("date2");
            province = (String)toolContext.getContext().get("province");
            section = (String)toolContext.getContext().get("section");
        }
        return jdbcTemplate.query("""
        SELECT
          json_value(metadata,'$.date') AS day,
          content,
          metadata
        FROM AI_VECTOR_STORE
        WHERE json_value(metadata,'$.province') = ?
          AND json_value(metadata,'$.section') = ?
          AND json_value(metadata,'$.date') IN (?, ?)
        ORDER BY day
    """, ComparisonPointDto.ROW_MAPPER,
                province, section, date1.toString(), date2.toString());
    }

    /* ============================================================
       5. Min / Max temporale
       ============================================================ */
    @Tool(
            name = "getMinMaxByPeriod",
            description = "Trova il giorno con valore minimo e massimo"
    )
    public List<MinMaxDto> getMinMaxByPeriod(ToolContext toolContext,
            String province,
            String section,
            int days
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            province = (String)toolContext.getContext().get("province");
            section = (String)toolContext.getContext().get("section");
            days =(int) toolContext.getContext().get("days");
        }
        return jdbcTemplate.query("""
        SELECT * FROM (
          SELECT
            json_value(metadata,'$.date') AS day,
            COUNT(*) AS total
          FROM AI_VECTOR_STORE
          WHERE json_value(metadata,'$.province') = ?
            AND json_value(metadata,'$.section') = ?
            AND json_value(metadata,'$.date') >= TRUNC(SYSDATE) - ?
          GROUP BY json_value(metadata,'$.date')
        )
        ORDER BY total DESC
    """, MinMaxDto.ROW_MAPPER,
                province, section, days);
    }

    /* ============================================================
       6. Aggregazione per sezione
       ============================================================ */
    @Tool(
            name = "aggregateBySection",
            description = "Aggrega i documenti per sezione"
    )
    public List<AggregationDto> aggregateBySection(ToolContext toolContext,
            LocalDate date,
            String province
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            province = (String)toolContext.getContext().get("province");
            date = (LocalDate) toolContext.getContext().get("date");
        }
        return jdbcTemplate.query("""
        SELECT
          json_value(metadata,'$.section') AS key,
          COUNT(*) AS total
        FROM AI_VECTOR_STORE
        WHERE json_value(metadata,'$.date') = ?
          AND json_value(metadata,'$.province') = ?
        GROUP BY json_value(metadata,'$.section')
    """, AggregationDto.ROW_MAPPER,
                date.toString(), province);
    }

    /* ============================================================
       7. Solo dati definitivi (partial = false)
       ============================================================ */
    @Tool(
            name = "getOnlyFinalData",
            description = "Recupera solo documenti non parziali"
    )
    public List<DocumentDto> getOnlyFinalData(ToolContext toolContext,
            LocalDate date,
            String province,
            String section
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            province = (String)toolContext.getContext().get("province");
            date = (LocalDate) toolContext.getContext().get("date");
            section = (String)toolContext.getContext().get("section");
        }
        return jdbcTemplate.query("""
        SELECT content, metadata
        FROM AI_VECTOR_STORE
        WHERE json_value(metadata,'$.date') = ?
          AND json_value(metadata,'$.province') = ?
          AND json_value(metadata,'$.section') = ?
          AND json_value(metadata,'$.partial') = 'false'
    """, DocumentDto.ROW_MAPPER,
                date.toString(), province, section);
    }

    /* ============================================================
       8. Aggregazione per source
       ============================================================ */
    @Tool(
            name = "aggregateBySource",
            description = "Aggrega i documenti per source"
    )
    public List<AggregationDto> aggregateBySource(ToolContext toolContext,
            LocalDate date,
            String province
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            province = (String)toolContext.getContext().get("province");
            date = (LocalDate) toolContext.getContext().get("date");
        }
        return jdbcTemplate.query("""
        SELECT
          json_value(metadata,'$.source') AS key,
          COUNT(*) AS total
        FROM AI_VECTOR_STORE
        WHERE json_value(metadata,'$.date') = ?
          AND json_value(metadata,'$.province') = ?
        GROUP BY json_value(metadata,'$.source')
    """, AggregationDto.ROW_MAPPER,
                date.toString(), province);
    }

    /* ============================================================
       9. Dataset pronto per grafici
       ============================================================ */
    @Tool(
            name = "getChartDataset",
            description = "Restituisce dataset pronto per grafici temporali"
    )
    public List<TrendPointDto> getChartDataset(ToolContext toolContext,
            String province,
            String section,
            int days
    ) {
        if(toolContext!=null){
            System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
            province = (String)toolContext.getContext().get("province");
            section = (String)toolContext.getContext().get("section");
            days =(int) toolContext.getContext().get("days");
        }
        return getTemporalTrend(toolContext,province, section, days);
    }

}

/* ========================= DTO ========================= */

record DocumentDto(String content, String metadata) { static final org.springframework.jdbc.core.RowMapper<DocumentDto> ROW_MAPPER = (rs, rowNum) -> new DocumentDto(rs.getString("content"), rs.getString("metadata")); }

record TrendPointDto(LocalDate day, int total) { static final org.springframework.jdbc.core.RowMapper<TrendPointDto> ROW_MAPPER = (rs, rowNum) -> new TrendPointDto( LocalDate.parse(rs.getString("day")), rs.getInt("total") ); }

record ComparisonPointDto(LocalDate day, String content, String metadata) { static final org.springframework.jdbc.core.RowMapper<ComparisonPointDto> ROW_MAPPER = (rs, rowNum) -> new ComparisonPointDto( LocalDate.parse(rs.getString("day")), rs.getString("content"), rs.getString("metadata") ); }

record MinMaxDto(LocalDate day, int total) { static final org.springframework.jdbc.core.RowMapper<MinMaxDto> ROW_MAPPER = (rs, rowNum) -> new MinMaxDto( LocalDate.parse(rs.getString("day")), rs.getInt("total") ); }

record AggregationDto(String key, int total) { static final org.springframework.jdbc.core.RowMapper<AggregationDto> ROW_MAPPER = (rs, rowNum) -> new AggregationDto( rs.getString("key"), rs.getInt("total") ); }


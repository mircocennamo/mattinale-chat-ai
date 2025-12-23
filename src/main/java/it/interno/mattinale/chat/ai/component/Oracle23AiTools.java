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


        /*
         * ============================================================
         * 1. Estrazione intero mattinale contenuti filtrati (drill-down)
         * ============================================================
         */
        @Tool(name = "getFullArticleByFilter", description = "Recupera l'intero mattinale  filtrati per range di date, provincia e sorgente")
        public List<DocumentDto> getFullArticleByFilter(ToolContext toolContext,
                                                      String province,
                                                        String source,
                                                      LocalDate date1,LocalDate date2,int days) {
                System.out.println("Parametri estratti dai parametri di getDocumentsByFilter  province=" + province  + ", source= " + source   + ",date1=" + date1 + ",date2=" + date2 + ", days=" + days);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        date1 = (LocalDate) toolContext.getContext().get("date1");
                        date2 = (LocalDate) toolContext.getContext().get("date2");
                        province = (String) toolContext.getContext().get("province");
                        source = (String) toolContext.getContext().get("source");
                        System.out.println("Pcontesto getDocumentsByFilter  province=" + province +  ", source= " + source   + ",date1=" + date1 + ",date2=" + date2 + ", days=" + days);

                }
                String section="fullArticle";
                return jdbcTemplate.query("""
                                    SELECT content, metadata
                                    FROM AI_VECTOR_STORE
                                    WHERE TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') BETWEEN ? AND ?
                                      AND json_value(metadata,'$.province') = ?
                                      AND json_value(metadata,'$.section') = ?
                                      AND json_value(metadata,'$.source') = ?
                                """, DocumentDto.ROW_MAPPER,
                        date1, date2 ,province, section, source);
        }



        /*
         * ============================================================
         * 1. Estrazione contenuti filtrati (drill-down)
         * ============================================================
         */
        @Tool(name = "getDocumentsByFilter", description = "Recupera i contenuti filtrati del mattinale per range di date, provincia, sezione e sorgente")
        public List<DocumentDto> getDocumentsByFilter(ToolContext toolContext,
                       String province,
                        String section,
                        String source,
                                                      LocalDate date1,LocalDate date2,int days) {
                System.out.println("Parametri estratti dai parametri di getDocumentsByFilter  province=" + province + ", section=" + section + ", source= " + source   + ",date1=" + date1 + ",date2=" + date2 + ", days=" + days);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        date1 = (LocalDate) toolContext.getContext().get("date1");
                        date2 = (LocalDate) toolContext.getContext().get("date2");
                        province = (String) toolContext.getContext().get("province");
                        section = (String) toolContext.getContext().get("section");
                        source = (String) toolContext.getContext().get("source");
                        System.out.println("Pcontesto getDocumentsByFilter  province=" + province + ", section=" + section + ", source= " + source   + ",date1=" + date1 + ",date2=" + date2 + ", days=" + days);

                }
                return jdbcTemplate.query("""
                                    SELECT content, metadata
                                    FROM AI_VECTOR_STORE
                                    WHERE TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') BETWEEN ? AND ?
                                      AND json_value(metadata,'$.province') = ?
                                      AND json_value(metadata,'$.section') = ?
                                      AND json_value(metadata,'$.source') = ?
                                """, DocumentDto.ROW_MAPPER,
                        date1, date2 ,province, section, source);
        }

        /*
         * ============================================================
         * 2. Conteggio semplice
         * ============================================================
         */
        @Tool(name = "countByFilter", description = "Conta i documenti in base ai metadata in un range di date, provincia, sezione e sorgente")
        public int countByFilter(ToolContext toolContext,
                                 String province,
                        String section,LocalDate date1,LocalDate date2,
                        String source) {
                System.out.println("Parametri estratti dai parametri di countByFilter  province=" + province + ", section=" + section + ", source=" + source + ",date1=" + date1 + ",date2=" + date2 );
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        province = (String) toolContext.getContext().get("province");
                        section = (String) toolContext.getContext().get("section");
                        source = (String) toolContext.getContext().get("source");
                        date1 = (LocalDate) toolContext.getContext().get("date1");
                        date2 = (LocalDate) toolContext.getContext().get("date2");
                        System.out.println("Parametri estratti dal contesto :  province="
                                + province + ", section=" + section + " , source= " + source + ",date1=" + date1 + ",date2=" + date2 );
                }
                return jdbcTemplate.queryForObject("""
                                    SELECT COUNT(*)
                                    FROM AI_VECTOR_STORE
                                    WHERE TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') BETWEEN ? AND ?
                                      AND json_value(metadata,'$.province') = ?
                                      AND json_value(metadata,'$.section') = ?
                                      AND json_value(metadata,'$.source') = ?
                                """, Integer.class,
                        date1, date2, province, section,source);
        }

        /*
         * ============================================================
         * 3. Andamento temporale (trend)
         * ============================================================
         */
        @Tool(name = "getTemporalTrend", description = "Restituisce l'andamento temporale aggregato")
        public List<TrendPointDto> getTemporalTrend(ToolContext toolContext,
                        String province,
                        String section,
                        String source,
                        LocalDate date1,
                        LocalDate date2,
                        int days) {
                System.out.println("Parametri estratti dai parametri di getTemporalTrend : province=" + province
                                + ", section=" + section + ", source= " + source + ",date1=" + date1 + ",date2=" + date2 + ", days=" + days);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        province = (String) toolContext.getContext().get("province");
                        section = (String) toolContext.getContext().get("section");
                        days = (int) toolContext.getContext().get("days");
                        date1 = (LocalDate) toolContext.getContext().get("date1");
                        date2 = (LocalDate) toolContext.getContext().get("date2");
                         source = (String) toolContext.getContext().get("source");
                        System.out.println("Parametri estratti dal contesto : province=" + province + ", section="
                                        + section + ", days=" + days + " , source= " + source);
                }

                return jdbcTemplate.query("""
                                    SELECT
                                       day,
                                       total
                                        FROM (
                                                 SELECT
                                                 json_value(metadata, '$.date') AS day,
                                                 SUM(CAST(json_value(content, '$.totali') AS NUMBER)) AS total
                                                 FROM AI_VECTOR_STORE
                                                 WHERE json_value(metadata, '$.province') = ?
                                                 AND json_value(metadata, '$.section')  = ?
                                                 AND json_value(metadata, '$.source') = ?
                                                 AND TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') BETWEEN ? AND ?
                                                 
                                                 GROUP BY json_value(metadata, '$.date')
                                                 ) t
                                      ORDER BY TO_DATE(t.day, 'YYYY-MM-DD');
                                """, TrendPointDto.ROW_MAPPER,
                                province, section, source,date1,date2);
        }

        /*
         * ============================================================
         * 4. Confronto due date
         * ============================================================
         */
        @Tool(name = "compareTwoDates", description = "Confronta i dettagli e il contenuto dei documenti tra due date per sezione e provincia")
        public List<ComparisonPointDto> compareTwoDates(ToolContext toolContext,
                        LocalDate date1,
                        LocalDate date2,
                        String province,
                        String section) {
                System.out.println("Parametri estratti dai parametri di compareTwoDates : date1=" + date1
                                + ", date2=" + date2 + ", province=" + province + ", section=" + section);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        date1 = (LocalDate) toolContext.getContext().get("date1");
                        date2 = (LocalDate) toolContext.getContext().get("date2");
                        province = (String) toolContext.getContext().get("province");
                        section = (String) toolContext.getContext().get("section");
                        System.out.println("contesto di compareTwoDates : date1=" + date1
                                + ", date2=" + date2 + ", province=" + province + ", section=" + section);
                }
                return jdbcTemplate.query("""
                                    SELECT
                                      json_value(metadata,'$.date') AS day,
                                      content,
                                      metadata
                                    FROM AI_VECTOR_STORE
                                    WHERE json_value(metadata,'$.province') = ?
                                      AND json_value(metadata,'$.section') = ?
                                      AND TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') BETWEEN ? AND ?
                                    ORDER BY day
                                """, ComparisonPointDto.ROW_MAPPER,
                                province, section, date1, date2);
        }

        /*
         * ============================================================
         * 5. Min / Max temporale  OK
         * ============================================================
         */
        @Tool(name = "getMinMaxByPeriod", description = "Trova il giorno con valore minimo e massimo")
        public List<MinMaxDto> getMinMaxByPeriod(ToolContext toolContext,
                        String province,
                        String section,LocalDate date1, LocalDate date2,
                        int days,String source) {
                System.out.println("Parametri estratti dai parametri di getMinMaxByPeriod : province=" + province
                                + ", section=" + section + "date1=" + date1 +  "date2=" + date2 +", days=" + days + ", source=" + source );
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        province = (String) toolContext.getContext().get("province");
                        section = (String) toolContext.getContext().get("section");
                        days = (int) toolContext.getContext().get("days");
                        date1 = (LocalDate) toolContext.getContext().get("date1");
                        date2 = (LocalDate) toolContext.getContext().get("date2");
                        source = (String) toolContext.getContext().get("source");
                        System.out.println("contesto getMinMaxByPeriod : province=" + province
                                + ", section=" + section + ", days=" + days +  " , source=" + source);
                }

                return jdbcTemplate.query("""
                                    SELECT * FROM (
                                      SELECT
                                        json_value(metadata,'$.date') AS day,
                                        SUM(CAST(json_value(content, '$.totali') AS NUMBER)) AS total
                                      FROM AI_VECTOR_STORE
                                      WHERE json_value(metadata,'$.province') = ?
                                        AND json_value(metadata,'$.section') = ?
                                        AND TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') BETWEEN ? AND ?
                                        AND json_value(metadata,'$.source') = ?
                                      GROUP BY json_value(metadata,'$.date')
                                    ) t
                                    ORDER BY t.total DESC
                                """, MinMaxDto.ROW_MAPPER,
                                province, section, date1 ,date2,source);
        }

        /*
         * ============================================================
         * 6. Aggregazione per sezione
         * ============================================================
         */
        @Tool(name = "aggregateBySection", description = "Aggrega i documenti per sezione")
        public List<AggregationDto> aggregateBySection(ToolContext toolContext,
                        LocalDate date,
                        String province) {
                System.out.println("Parametri estratti dai parametri di aggregateBySection : date=" + date
                                + ", province=" + province);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        province = (String) toolContext.getContext().get("province");
                        date = (LocalDate) toolContext.getContext().get("date");
                        System.out.println("contesto aggregateBySection : date=" + date
                                + ", province=" + province);
                }
                return jdbcTemplate.query("""
                                    SELECT
                                      json_value(metadata,'$.section') AS key,
                                      SUM(CAST(json_value(content, '$.totali') AS NUMBER)) AS total
                                    FROM AI_VECTOR_STORE
                                    WHERE TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') = ?
                                      AND json_value(metadata,'$.province') = ?
                                      GROUP BY json_value(metadata, '$.section')
                                      ORDER BY total DESC;
                                """, AggregationDto.ROW_MAPPER,
                                date, province);
        }

        /*
         * ============================================================
         * 7. Solo dati definitivi (partial = false)
         * ============================================================
         */
        @Tool(name = "getOnlyFinalData", description = "Recupera solo documenti non parziali")
        public List<DocumentDto> getOnlyFinalData(ToolContext toolContext,
                        LocalDate date,
                        String province,
                        String section) {
                System.out.println("Parametri estratti dai parametri di getOnlyFinalData : date=" + date +  ", province=" + province + ", section=" + section);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        province = (String) toolContext.getContext().get("province");
                        date = (LocalDate) toolContext.getContext().get("date");
                        section = (String) toolContext.getContext().get("section");
                        System.out.println("contesto getOnlyFinalData : date=" + date +  ", province=" + province + ", section=" + section);
                }
                return jdbcTemplate.query("""
                                    SELECT content, metadata
                                    FROM AI_VECTOR_STORE
                                    WHERE TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') = ?
                                      AND json_value(metadata,'$.province') = ?
                                      AND json_value(metadata,'$.section') = ?
                                      AND json_value(metadata,'$.partial') = 'false'
                                """, DocumentDto.ROW_MAPPER,
                                date, province, section);
        }

        /*
         * ============================================================
         * 8. Aggregazione per source
         * ============================================================
         */
        @Tool(name = "aggregateBySource", description = "Aggrega i documenti per source")
        public List<AggregationDto> aggregateBySource(ToolContext toolContext,
                        LocalDate date,
                        String province) {
                System.out.println("Parametri estratti dai parametri di aggregateBySource : date=" + date +  ", province=" + province);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        province = (String) toolContext.getContext().get("province");
                        date = (LocalDate) toolContext.getContext().get("date");
                        System.out.println("contesto aggregateBySource : date=" + date +  ", province=" + province);
                }


                return jdbcTemplate.query("""
                                    SELECT
                                      json_value(metadata,'$.source') AS key,
                                      SUM(CAST(json_value(content, '$.totali') AS NUMBER)) AS total
                                    FROM AI_VECTOR_STORE
                                    WHERE TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') = ?
                                      AND json_value(metadata,'$.province') = ?
                                    GROUP BY json_value(metadata,'$.source')
                                """, AggregationDto.ROW_MAPPER,
                                date, province);
        }

        /*
         * ============================================================
         * 9. Dataset pronto per grafici
         * ============================================================
         */
        @Tool(name = "getChartDataset", description = "Restituisce dataset pronto per grafici temporali")
        public List<TrendPointDto> getChartDataset(ToolContext toolContext,
                        String province,
                        String section, String source,LocalDate date1,
                                                   LocalDate date2,
                        int days) {
                System.out.println("Parametri estratti dai parametri di getChartDataset : province=" + province  + ", section=" + section + ", source= " + source +  ", date1= " + date1 +  ", date2= " + date2 +", days=" + days);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        province = (String) toolContext.getContext().get("province");
                        section = (String) toolContext.getContext().get("section");
                        days = (int) toolContext.getContext().get("days");
                        source = (String) toolContext.getContext().get("source");
                        date1 = (LocalDate) toolContext.getContext().get("date1");
                        date2 = (LocalDate) toolContext.getContext().get("date2");
                        System.out.println("Contesto getChartDataset : province=" + province  + ", section=" + section + ", source= " + source +  ", date1= " + date1 +  ", date2= " + date2 +", days=" + days);
                }
                return getTemporalTrend(toolContext, province, section, source, date1,date2,days);
        }





        /*
         * ============================================================
         * 10. Ricerca per Keyword
         * ============================================================
         */
        @Tool(name = "searchKeywords", description = "Cerca documenti che contengono una specifica parola chiave nel contenuto")
        public List<DocumentDto> searchKeywords(ToolContext toolContext,
                        String keyword,
                        String province,
                        String section,LocalDate date1,
                                                   LocalDate date2,
                        int days) {
                System.out.println("Parametri estratti dai parametri di searchKeywords : keyword=" + keyword + ", province=" + province + ", section=" + section + ", days=" + days);
                if (toolContext != null) {
                        System.out.println("ToolContext presente, estraggo i parametri dal contesto...");
                        keyword = (String) toolContext.getContext().get("keyword");
                        province = (String) toolContext.getContext().get("province");
                        section = (String) toolContext.getContext().get("section");
                        days = (int) toolContext.getContext().get("days");
                        System.out.println("contesto searchKeywords : keyword=" + keyword + ", province=" + province + ", section=" + section + ", days=" + days);
                }

                // Costruisci la query base
                String sql = """
                                SELECT content, metadata
                                FROM AI_VECTOR_STORE
                                WHERE lower(content) LIKE lower(?)
                                """;

                // Parametri dinamici
                java.util.ArrayList<Object> params = new java.util.ArrayList<>();
                params.add("%" + keyword + "%");

                if (province != null && !province.isEmpty()) {
                        sql += " AND json_value(metadata,'$.province') = ? ";
                        params.add(province);
                }
                if (section != null && !section.isEmpty()) {
                        sql += " AND json_value(metadata,'$.section') = ? ";
                        params.add(section);
                }
                if (days > 0) {
                        sql += "AND TO_DATE(json_value(metadata, '$.date'), 'YYYY-MM-DD') BETWEEN ? AND ?";
                        params.add(date1);
                        params.add(date2);
                }

                return jdbcTemplate.query(sql, DocumentDto.ROW_MAPPER, params.toArray());
        }

        /*
         * ============================================================
         * 11. Valori Distinti Metadata
         * ============================================================
         */
        @Tool(name = "getDistinctMetadata", description = "Restituisce i valori unici disponibili per un campo metadata (province, section, source)")
        public List<String> getDistinctMetadata(ToolContext toolContext, String fieldName) {
                System.out.println("Parametri estratti dai parametri di getDistinctMetadata : fieldName=" + fieldName);
                if (toolContext != null) {
                        fieldName = (String) toolContext.getContext().get("fieldName");
                        System.out.println("contesto getDistinctMetadata : fieldName=" + fieldName);
                }

                // Whitelist per sicurezza
                if (!List.of("province", "section", "source").contains(fieldName)) {
                        throw new IllegalArgumentException("Campo non valido per distinct: " + fieldName);
                }

                String sql = String.format("""
                                SELECT DISTINCT json_value(metadata, '$.%s') AS val
                                FROM AI_VECTOR_STORE
                                WHERE json_value(metadata, '$.%s') IS NOT NULL
                                ORDER BY val
                                """, fieldName, fieldName);

                return jdbcTemplate.queryForList(sql, String.class);
        }




}

/* ========================= DTO ========================= */

record AggregationDto(String key, int total) {
        static final org.springframework.jdbc.core.RowMapper<AggregationDto> ROW_MAPPER = (rs,
                        rowNum) -> new AggregationDto(rs.getString("key"), rs.getInt("total"));
}



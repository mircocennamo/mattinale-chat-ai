package it.interno.mattinale.chat.ai.component;

public record DocumentDto(String content, String metadata) {
        static final org.springframework.jdbc.core.RowMapper<DocumentDto> ROW_MAPPER = (rs,
                        rowNum) -> new DocumentDto(rs.getString("content"), rs.getString("metadata"));
}

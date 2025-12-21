package it.interno.mattinale.chat.ai.model;




public record DocumentDTO (String content, String metadata) {
    public static final org.springframework.jdbc.core.RowMapper<DocumentDTO>
        ROW_MAPPER = (rs, rowNum) -> new DocumentDTO(rs.getString("content"), rs.getString("metadata")); }

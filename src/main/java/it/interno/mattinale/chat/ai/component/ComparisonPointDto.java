package it.interno.mattinale.chat.ai.component;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record ComparisonPointDto(

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate day,
        String content,
        String metadata) {
        static final org.springframework.jdbc.core.RowMapper<ComparisonPointDto> ROW_MAPPER = (rs,
                        rowNum) -> new ComparisonPointDto(LocalDate.parse(rs.getString("day")), rs.getString("content"),
                                        rs.getString("metadata"));
}

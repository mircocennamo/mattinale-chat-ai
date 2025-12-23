package it.interno.mattinale.chat.ai.component;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record MinMaxDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate day,


                        int total) {
        static final org.springframework.jdbc.core.RowMapper<MinMaxDto> ROW_MAPPER = (rs,
                        rowNum) -> new MinMaxDto(LocalDate.parse(rs.getString("day")), rs.getInt("total"));
}

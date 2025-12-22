package it.interno.mattinale.chat.ai.model;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record DataRange(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @DefaultValue("1970-01-01")
        LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @DefaultValue("2100-12-31")
        LocalDate to,
        Integer days
) {


        public static DataRange defaultRange() {
                //days differenza tra le due date
                LocalDate from = LocalDate.of(1970, 1, 1);
                LocalDate to = LocalDate.of(2100, 12, 31);
                int days = (int) (to.toEpochDay() - from.toEpochDay());
        return new DataRange(from, to, days);

        }
}

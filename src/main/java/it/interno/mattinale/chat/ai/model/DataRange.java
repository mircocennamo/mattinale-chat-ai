package it.interno.mattinale.chat.ai.model;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record DataRange(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,
        Integer days
) {
}

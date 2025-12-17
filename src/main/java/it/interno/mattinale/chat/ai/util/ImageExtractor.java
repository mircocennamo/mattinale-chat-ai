package it.interno.mattinale.chat.ai.util;


import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Optional;

public class ImageExtractor {

    // Cattura un data URL PNG (prima occorrenza)
    private static final Pattern PNG_DATA_URL =
            Pattern.compile("data:image/png;base64,[A-Za-z0-9+/=]+");

    /**
     * Estrae il primo data URL PNG dalla risposta.
     *
     * @param resp testo contenente eventualmente un data URL
     * @return Optional con il data URL completo, altrimenti empty
     */
    public static Optional<String> extractFirstPngDataUrl(String resp) {
        if (resp == null) return Optional.empty();

        // Normalizza: rimuove spazi e a capo che i modelli talvolta inseriscono
        String normalized = resp.replaceAll("\\s+", "");

        Matcher m = PNG_DATA_URL.matcher(normalized);
        if (m.find()) {
            return Optional.of(m.group());
        }
        return Optional.empty();
    }
}


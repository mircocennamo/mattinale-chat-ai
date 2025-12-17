package it.interno.mattinale.chat.ai.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProvinceDictionary {

    public static class Province {
        public String code;   // Sigla a 2 lettere, es. RM
        public String name;   // Nome esteso, es. Roma
        public String region; // Regione

        // alias calcolati a runtime
        Set<String> normalizedAliases;
    }

    public static class MatchResult {
        public String canonicalName; // MAIUSCOLO
        public String code;          // Sigla
        public boolean ambiguous;
        public double confidence;    // 0..1
        public List<String> candidates; // elenco dei nomi canonici se ambiguo
        public String matchedAlias;
    }

    private final Map<String, Province> byCode = new HashMap<>();
    private final Map<String, Province> byCanonicalName = new HashMap<>();
    private final List<Province> all = new ArrayList<>();

    public ProvinceDictionary() {
        load();
    }

    private void load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = new ClassPathResource("provinces.json").getInputStream()) {
            List<Province> list = mapper.readValue(is, new TypeReference<>() {});
            for (Province p : list) {
                if (p.code == null || p.name == null) continue;
                p.normalizedAliases = buildAliases(p);
                all.add(p);
                byCode.put(p.code.toUpperCase(Locale.ITALY), p);
                byCanonicalName.put(p.name.toUpperCase(Locale.ITALY), p);
            }
        } catch (IOException e) {
            throw new RuntimeException("Impossibile caricare provinces.json", e);
        }
    }

    private Set<String> buildAliases(Province p) {
        // Genera varianti normalizzate: con/senza preposizioni interne, apostrofi, trattini
        Set<String> a = new LinkedHashSet<>();
        String name = p.name;
        a.add(normalize(name));

        // Varianti comuni per alcuni casi: Reggio di Calabria/Emilia, La Spezia, L'Aquila, Monza e della Brianza, Massa-Carrara
        String simplified = name
                .replace(" di ", " ")
                .replace(" nell'", " ")
                .replace(" all'", " ")
                .replace(" del ", " ")
                .replace(" della ", " ")
                .replace(" degli ", " ")
                .replace(" delle ", " ")
                .replace(" e ", " ")
                .replace("-", " ")
                .replace("/", " ")
                .replace("'", " ")
                .replace("  ", " ")
                .trim();
        a.add(normalize(simplified));

        // Aggiungi codice sigla come alias normalizzato (ma attenzione a TO/PO ecc.)
        a.add(normalize(p.code));

        // varianti specifiche
        if (name.toLowerCase(Locale.ITALY).contains("reggio")) {
            if (name.toLowerCase(Locale.ITALY).contains("calabria")) {
                a.add(normalize("reggio calabria"));
            } else if (name.toLowerCase(Locale.ITALY).contains("emilia")) {
                a.add(normalize("reggio emilia"));
            }
        }
        if (name.equalsIgnoreCase("La Spezia")) {
            a.add(normalize("spezia"));
            a.add(normalize("laspezia"));
        }
        if (name.equalsIgnoreCase("L'Aquila")) {
            a.add(normalize("laquila"));
        }
        if (name.equalsIgnoreCase("Forlì-Cesena")) {
            a.add(normalize("forli cesena"));
            a.add(normalize("forli"));
            a.add(normalize("cesena"));
        }
        if (name.equalsIgnoreCase("Massa-Carrara")) {
            a.add(normalize("massa carrara"));
            a.add(normalize("massa"));
            a.add(normalize("carrara"));
        }
        if (name.equalsIgnoreCase("Monza e della Brianza")) {
            a.add(normalize("monza"));
            a.add(normalize("brianza"));
        }
        if (name.equalsIgnoreCase("Bolzano/Bozen")) {
            a.add(normalize("bolzano"));
            a.add(normalize("bozen"));
        }
        if (name.equalsIgnoreCase("Trento")) {
            a.add(normalize("trentino"));
        }
        if (name.equalsIgnoreCase("Sud Sardegna")) {
            a.add(normalize("medio campidano")); // storico
            a.add(normalize("sud sardegna"));
        }

        return a;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ITALY).trim();
        // rimuovi accenti
        String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // uniforma apostrofi diversi
        noAccent = noAccent.replace("’", "'");
        // rimuovi apostrofi, punteggiatura non alfabetica, spazi
        String cleaned = noAccent.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    public MatchResult matchProvince(String text) {
        MatchResult res = new MatchResult();
        if (text == null || text.isBlank()) return res;

        String normText = normalize(text);

        // 1) Pattern espliciti con preposizioni e forme istituzionali
        String[] patterns = new String[]{
                "(?:provincia|citta metropolitana|citta' metropolitana|area metropolitana|capoluogo) di\\s+([a-z0-9 ']+)",
                "(?:a|in|di|nel|nella|nello|nei|nelle|allo|alla|all|dello|della|dei|degli|delle)\\s+([a-z0-9 ']+)"
        };

        for (String pat : patterns) {
            Matcher m = Pattern.compile(pat).matcher(normText);
            if (m.find()) {
                String candidate = m.group(1).trim();
                MatchResult mr = resolveCandidate(candidate, normText);
                if (mr != null) return mr;
            }
        }

        // 2) Ricerca fallback: scan alias
        List<Province> hits = new ArrayList<>();
        String matchedAlias = null;
        for (Province p : all) {
            for (String al : p.normalizedAliases) {
                if (al.length() < 2) continue;
                if (containsWord(normText, al)) {
                    hits.add(p);
                    matchedAlias = al;
                    break;
                }
            }
        }
        if (hits.isEmpty()) return res;
        if (hits.size() == 1) {
            Province p = hits.get(0);
            res.canonicalName = p.name.toUpperCase(Locale.ITALY);
            res.code = p.code.toUpperCase(Locale.ITALY);
            res.ambiguous = false;
            res.confidence = 0.7;
            res.matchedAlias = matchedAlias;
            return res;
        }

        // 3) Euristiche per casi noti: Reggio
        boolean mentionsCalabria = normText.contains("calabria");
        boolean mentionsEmilia = normText.contains("emilia") || normText.contains("romagna");
        Optional<Province> rc = hits.stream().filter(p -> "RC".equalsIgnoreCase(p.code)).findFirst();
        Optional<Province> re = hits.stream().filter(p -> "RE".equalsIgnoreCase(p.code)).findFirst();
        if (rc.isPresent() && re.isPresent()) {
            if (mentionsCalabria && !mentionsEmilia) return mk(rc.get(), 0.8, matchedAlias);
            if (mentionsEmilia && !mentionsCalabria) return mk(re.get(), 0.8, matchedAlias);
            // ambigua
            res.ambiguous = true;
            res.candidates = List.of(rc.get().name.toUpperCase(Locale.ITALY), re.get().name.toUpperCase(Locale.ITALY));
            return res;
        }

        // Se più hit ma non casi speciali: segna ambigua
        res.ambiguous = true;
        List<String> cands = new ArrayList<>();
        for (Province p : hits) cands.add(p.name.toUpperCase(Locale.ITALY));
        res.candidates = cands;
        return res;
    }

    private MatchResult resolveCandidate(String candidateRaw, String normText) {
        String cand = candidateRaw;
        // tronca eventuali parole di coda comuni (es. "di roma città")
        // Teniamo solo le prime 3 parole per evitare trascinamenti troppo lunghi
        String[] parts = cand.split(" ");
        if (parts.length > 3) cand = String.join(" ", Arrays.copyOf(parts, 3));
        String n = normalize(cand);

        // per match proviamo per codice, per nome canonico e per alias
        // codice: se due lettere
        if (n.length() == 2) {
            Province byC = byCode.get(n.toUpperCase(Locale.ITALY));
            if (byC != null) return mk(byC, 0.95, n);
        }

        // nome esatto
        for (Province p : all) {
            if (normalize(p.name).equals(n)) return mk(p, 0.95, n);
        }
        // alias
        for (Province p : all) {
            if (p.normalizedAliases.contains(n)) return mk(p, 0.9, n);
        }

        // Parziale: cerca province il cui alias inizia con n
        List<Province> partial = new ArrayList<>();
        for (Province p : all) {
            for (String al : p.normalizedAliases) {
                if (al.startsWith(n) && n.length() >= 3) {
                    partial.add(p);
                    break;
                }
            }
        }
        if (partial.size() == 1) return mk(partial.get(0), 0.6, n);
        if (partial.size() > 1) {
            // stesso trattamento ambiguità (es. reggio)
            MatchResult res = new MatchResult();
            res.ambiguous = true;
            List<String> cands = new ArrayList<>();
            for (Province p : partial) cands.add(p.name.toUpperCase(Locale.ITALY));
            res.candidates = cands;
            return res;
        }
        return null;
    }

    private static boolean containsWord(String textNorm, String aliasNorm) {
        // match come parola o sottostringa robusta
        return Pattern.compile("\\b" + Pattern.quote(aliasNorm) + "\\b").matcher(textNorm).find()
                || textNorm.contains(" " + aliasNorm + " ")
                || textNorm.endsWith(" " + aliasNorm)
                || textNorm.startsWith(aliasNorm + " ");
    }

    private MatchResult mk(Province p, double conf, String matchedAlias) {
        MatchResult res = new MatchResult();
        res.canonicalName = p.name.toUpperCase(Locale.ITALY);
        res.code = p.code.toUpperCase(Locale.ITALY);
        res.ambiguous = false;
        res.confidence = conf;
        res.matchedAlias = matchedAlias;
        return res;
    }
}

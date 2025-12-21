package it.interno.mattinale.chat.ai.tool;

import it.interno.mattinale.chat.ai.service.RagChartService;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;

@Component
public class ChartTools {

    private final RagChartService ragChartService;

    public ChartTools(RagChartService ragChartService) {
        this.ragChartService = ragChartService;
    }

    /** Tool: genera un grafico a barre e restituisce PNG in base64. */
    @Tool(description = "Genera un grafico a barre dai dati forniti")
    public String barChart(BarChartInput input) {
        // Costruisci mappa ordinata label->valore
        System.out.println("Generando grafico a barre con " + input.labels().size() + " etichette.");
        java.util.Map<String, Integer> data = new java.util.LinkedHashMap<>();
        for (int i = 0; i < Math.min(input.labels().size(), input.values().size()); i++) {
            data.put(input.labels().get(i), input.values().get(i));
        }

        // 1. Definisci directory di output
        String userHome = System.getProperty("user.dir");
        java.nio.file.Path outputDir = java.nio.file.Paths.get(userHome, "images");
        try {
            java.nio.file.Files.createDirectories(outputDir);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Impossibile creare directory immagini su " + outputDir, e);
        }

        // 2. Genera nome file univoco
        String filename = "chart_" + System.currentTimeMillis() + "_"
                + java.util.UUID.randomUUID().toString().substring(0, 8) + ".png";
        java.nio.file.Path destination = outputDir.resolve(filename);

        // 3. Salva su disco
        ragChartService.saveBarChart(
                data,
                input.title(),
                input.xLabel(),
                input.yLabel(),
                destination);

        // 4. Restituisce URL (o markdown image tag)
        // Nota: Spring Boot servirà /images/** -> ~/mattinale-images/**
        String url = "/images/" + filename;
        System.out.println("Grafico salvato su " + destination + ", accessibile a " + url);
        return "Grafico generato e disponibile a: " + url;
    }

    /** Input del tool: un unico DTO */
    public static record BarChartInput(
            String title,
            java.util.List<String> labels,
            java.util.List<Integer> values,
            String xLabel,
            String yLabel) {
    }
}

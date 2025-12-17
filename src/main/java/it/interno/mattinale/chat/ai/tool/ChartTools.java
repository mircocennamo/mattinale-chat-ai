package it.interno.mattinale.chat.ai.tool;

import it.interno.mattinale.chat.ai.RagChartService;
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
    public ChartResult barChart(BarChartInput input) {
        // Costruisci mappa ordinata label->valore
        System.out.println("Generando grafico a barre con " + input.labels().size() + " etichette.");
        java.util.Map<String,Integer> data = new java.util.LinkedHashMap<>();
        for (int i = 0; i < Math.min(input.labels().size(), input.values().size()); i++) {
            data.put(input.labels().get(i), input.values().get(i));
        }

        byte[] png = ragChartService.buildBarChartPng(
                data,
                input.title(),
                input.xLabel(),
                input.yLabel()
        );
        String base64 = java.util.Base64.getEncoder().encodeToString(png);
        return new ChartResult(base64);
    }

    /** Input del tool: un unico DTO */
    public static record BarChartInput(
            String title,
            java.util.List<String> labels,
            java.util.List<Integer> values,
            String xLabel,
            String yLabel
    ) {}

    /** Output del tool */
    public static record ChartResult(String pngBase64) {}
}

package it.interno.mattinale.chat.ai.service;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Map;

@Service
public class RagChartService {

    /** Crea un grafico a barre e restituisce PNG come bytes */
    public byte[] buildBarChartPng(Map<String, Integer> data, String title, String xLabel, String yLabel) {
        var labels = new ArrayList<>(data.keySet());
        var values = new ArrayList<>(data.values());

        CategoryChart chart = new CategoryChartBuilder()
                .width(900).height(580)
                .title(title)
                .xAxisTitle(xLabel)
                .yAxisTitle(yLabel)
                .build();

        // Stile
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setPlotGridVerticalLinesVisible(false);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBorderVisible(false);
        chart.getStyler().setYAxisMin(0.0);

        chart.addSeries("Valori", labels, values);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Errore creazione PNG del grafico", e);
        }
    }

    /** Crea un grafico a barre e lo salva su file */
    public void saveBarChart(Map<String, Integer> data, String title, String xLabel, String yLabel,
            java.nio.file.Path destinationPath) {
        var labels = new ArrayList<>(data.keySet());
        var values = new ArrayList<>(data.values());

        CategoryChart chart = new CategoryChartBuilder()
                .width(900).height(580)
                .title(title)
                .xAxisTitle(xLabel)
                .yAxisTitle(yLabel)
                .build();

        // Stile
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setPlotGridVerticalLinesVisible(false);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBorderVisible(false);
        chart.getStyler().setYAxisMin(0.0);

        chart.addSeries("Valori", labels, values);

        try {
            BitmapEncoder.saveBitmap(chart, destinationPath.toString(), BitmapEncoder.BitmapFormat.PNG);
        } catch (Exception e) {
            throw new RuntimeException("Errore salvataggio PNG del grafico su " + destinationPath, e);
        }
    }

}

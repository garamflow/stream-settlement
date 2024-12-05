package com.github.garamflow.streamsettlement.batch.performance.util;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PerformanceVisualizer {

    public static void createPerformanceChart(
            String title,
            List<Integer> xValues,
            List<List<Double>> yValuesList,
            List<String> seriesNames,
            String xAxisLabel,
            String yAxisLabel,
            String outputPath) throws IOException {

        if (yValuesList == null || yValuesList.isEmpty() || xValues == null || xValues.isEmpty()) {
            return;
        }

        XYSeriesCollection dataset = new XYSeriesCollection();

        for (int seriesIndex = 0; seriesIndex < yValuesList.size(); seriesIndex++) {
            List<Double> yValues = yValuesList.get(seriesIndex);
            if (yValues == null || yValues.isEmpty()) {
                continue;
            }

            XYSeries series = new XYSeries(seriesNames.get(seriesIndex));
            for (int i = 0; i < xValues.size() && i < yValues.size(); i++) {
                series.add(xValues.get(i), yValues.get(i));
            }
            dataset.addSeries(series);
        }

        if (dataset.getSeriesCount() == 0) {
            return;
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                xAxisLabel,
                yAxisLabel,
                dataset
        );

        chart.setBackgroundPaint(java.awt.Color.WHITE);
        chart.getPlot().setBackgroundPaint(new java.awt.Color(240, 240, 240));

        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setDefaultStroke(new java.awt.BasicStroke(2.0f));
        plot.setRenderer(renderer);

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();
        ChartUtils.saveChartAsPNG(outputFile, chart, 800, 600);
    }
}
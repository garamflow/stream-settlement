package com.github.garamflow.streamsettlement.batch.performance.util;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
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
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();

        XYSeriesCollection dataset = new XYSeriesCollection();

        for (int seriesIndex = 0; seriesIndex < yValuesList.size(); seriesIndex++) {
            XYSeries series = new XYSeries(seriesNames.get(seriesIndex));
            List<Double> yValues = yValuesList.get(seriesIndex);

            for (int i = 0; i < xValues.size(); i++) {
                series.add(xValues.get(i), yValues.get(i));
            }
            dataset.addSeries(series);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                xAxisLabel,
                yAxisLabel,
                dataset
        );

        if (yAxisLabel.contains("데드락")) {
            NumberAxis yAxis = (NumberAxis) chart.getXYPlot().getRangeAxis();
            yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        }

        ChartUtils.saveChartAsPNG(
                outputFile,
                chart,
                800,
                600
        );
    }
}
package com.trading.journal.service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.springframework.stereotype.Service;

/** 차트 이미지 생성 서비스 (JFreeChart 사용) */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartGenerationService {

    private static final int DEFAULT_WIDTH = 600;
    private static final int DEFAULT_HEIGHT = 400;

    // 색상 팔레트
    private static final Color PRIMARY_COLOR = new Color(13, 110, 253); // Bootstrap primary
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94); // Green
    private static final Color DANGER_COLOR = new Color(239, 68, 68); // Red
    private static final Color WARNING_COLOR = new Color(245, 158, 11); // Amber
    private static final Color INFO_COLOR = new Color(6, 182, 212); // Cyan

    private static final Color[] CHART_COLORS = {
        PRIMARY_COLOR,
        SUCCESS_COLOR,
        WARNING_COLOR,
        DANGER_COLOR,
        INFO_COLOR,
        new Color(139, 92, 246), // Violet
        new Color(236, 72, 153), // Pink
        new Color(20, 184, 166), // Teal
        new Color(249, 115, 22), // Orange
        new Color(132, 204, 22) // Lime
    };

    /** 자산 곡선 (Equity Curve) 라인 차트 생성 */
    public byte[] generateEquityCurveChart(List<String> labels, List<BigDecimal> values)
            throws IOException {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("포트폴리오 가치");

        for (int i = 0; i < values.size(); i++) {
            series.add(i, values.get(i).doubleValue());
        }
        dataset.addSeries(series);

        JFreeChart chart =
                ChartFactory.createXYLineChart(
                        "자산 곡선 (Equity Curve)",
                        "기간",
                        "자산 가치 (₩)",
                        dataset,
                        PlotOrientation.VERTICAL,
                        true,
                        true,
                        false);

        customizeLineChart(chart);
        return chartToBytes(chart, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /** 포트폴리오 구성 파이 차트 생성 */
    public byte[] generatePortfolioPieChart(Map<String, BigDecimal> holdings) throws IOException {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();

        holdings.forEach((symbol, value) -> dataset.setValue(symbol, value.doubleValue()));

        JFreeChart chart = ChartFactory.createPieChart("포트폴리오 구성", dataset, true, true, false);

        customizePieChart(chart);
        return chartToBytes(chart, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /** 섹터별 배분 파이 차트 생성 */
    public byte[] generateSectorAllocationChart(Map<String, BigDecimal> sectorWeights)
            throws IOException {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();

        sectorWeights.forEach((sector, weight) -> dataset.setValue(sector, weight.doubleValue()));

        JFreeChart chart = ChartFactory.createPieChart("섹터별 배분", dataset, true, true, false);

        customizePieChart(chart);
        return chartToBytes(chart, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /** 월별 손익 바 차트 생성 */
    public byte[] generateMonthlyProfitChart(Map<String, BigDecimal> monthlyProfits)
            throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        monthlyProfits.forEach(
                (month, profit) -> dataset.addValue(profit.doubleValue(), "손익", month));

        JFreeChart chart =
                ChartFactory.createBarChart(
                        "월별 손익",
                        "월",
                        "손익 (₩)",
                        dataset,
                        PlotOrientation.VERTICAL,
                        false,
                        true,
                        false);

        customizeBarChart(chart, true);
        return chartToBytes(chart, DEFAULT_WIDTH, 300);
    }

    /** 수익/손실 분포 바 차트 생성 */
    public byte[] generateProfitDistributionChart(int winCount, int lossCount) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        // 서로 다른 시리즈를 사용하여 색상 구분
        dataset.addValue(winCount, "수익", "수익 거래");
        dataset.addValue(lossCount, "손실", "손실 거래");

        JFreeChart chart =
                ChartFactory.createBarChart(
                        "수익/손실 거래 분포",
                        "",
                        "거래 수",
                        dataset,
                        PlotOrientation.VERTICAL,
                        true,
                        true,
                        false);

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();

        // 시리즈별 색상 설정
        renderer.setSeriesPaint(0, SUCCESS_COLOR); // 수익: 녹색
        renderer.setSeriesPaint(1, DANGER_COLOR); // 손실: 빨간색

        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        renderer.setDrawBarOutline(false);
        renderer.setShadowVisible(false);

        return chartToBytes(chart, 400, 300);
    }

    /** 낙폭 (Drawdown) 영역 차트 생성 */
    public byte[] generateDrawdownChart(List<String> labels, List<BigDecimal> drawdowns)
            throws IOException {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Drawdown");

        for (int i = 0; i < drawdowns.size(); i++) {
            series.add(i, drawdowns.get(i).doubleValue());
        }
        dataset.addSeries(series);

        JFreeChart chart =
                ChartFactory.createXYLineChart(
                        "낙폭 (Drawdown)",
                        "기간",
                        "낙폭 (%)",
                        dataset,
                        PlotOrientation.VERTICAL,
                        false,
                        true,
                        false);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, DANGER_COLOR);
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        return chartToBytes(chart, DEFAULT_WIDTH, 250);
    }

    /** 요일별 성과 바 차트 생성 */
    public byte[] generateDayOfWeekChart(Map<String, BigDecimal> dayOfWeekReturns)
            throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        String[] days = {"월", "화", "수", "목", "금"};
        for (String day : days) {
            BigDecimal returnPct = dayOfWeekReturns.getOrDefault(day, BigDecimal.ZERO);
            dataset.addValue(returnPct.doubleValue(), "수익률", day);
        }

        JFreeChart chart =
                ChartFactory.createBarChart(
                        "요일별 평균 수익률",
                        "요일",
                        "수익률 (%)",
                        dataset,
                        PlotOrientation.VERTICAL,
                        false,
                        true,
                        false);

        customizeBarChart(chart, true);
        return chartToBytes(chart, 500, 300);
    }

    /** 벤치마크 비교 라인 차트 생성 */
    public byte[] generateBenchmarkComparisonChart(
            List<String> labels,
            List<BigDecimal> portfolioReturns,
            List<BigDecimal> benchmarkReturns,
            String benchmarkName)
            throws IOException {

        XYSeriesCollection dataset = new XYSeriesCollection();

        XYSeries portfolioSeries = new XYSeries("포트폴리오");
        XYSeries benchmarkSeries = new XYSeries(benchmarkName);

        for (int i = 0; i < portfolioReturns.size(); i++) {
            portfolioSeries.add(i, portfolioReturns.get(i).doubleValue());
            if (i < benchmarkReturns.size()) {
                benchmarkSeries.add(i, benchmarkReturns.get(i).doubleValue());
            }
        }

        dataset.addSeries(portfolioSeries);
        dataset.addSeries(benchmarkSeries);

        JFreeChart chart =
                ChartFactory.createXYLineChart(
                        "포트폴리오 vs " + benchmarkName,
                        "기간",
                        "수익률 (%)",
                        dataset,
                        PlotOrientation.VERTICAL,
                        true,
                        true,
                        false);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, PRIMARY_COLOR);
        renderer.setSeriesPaint(1, new Color(128, 128, 128));
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesShapesVisible(1, false);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(
                1,
                new BasicStroke(
                        2.0f,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        10.0f,
                        new float[] {5.0f},
                        0.0f));
        plot.setRenderer(renderer);

        return chartToBytes(chart, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    // === 차트 스타일 커스터마이징 ===

    private void customizeLineChart(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, PRIMARY_COLOR);
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setNumberFormatOverride(new DecimalFormat("#,###"));
    }

    private void customizePieChart(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);

        @SuppressWarnings("unchecked")
        PiePlot<String> plot = (PiePlot<String>) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);

        // 색상 적용
        int colorIndex = 0;
        for (Object key : plot.getDataset().getKeys()) {
            plot.setSectionPaint(
                    (Comparable<?>) key, CHART_COLORS[colorIndex % CHART_COLORS.length]);
            colorIndex++;
        }

        plot.setLabelBackgroundPaint(new Color(255, 255, 255, 200));
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);

        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setPosition(RectangleEdge.RIGHT);
            legend.setBackgroundPaint(Color.WHITE);
        }
    }

    private void customizeBarChart(JFreeChart chart, boolean colorByValue) {
        chart.setBackgroundPaint(Color.WHITE);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        BarRenderer renderer = (BarRenderer) plot.getRenderer();

        if (colorByValue) {
            // 값에 따라 색상 설정 (양수: 녹색, 음수: 빨간색)
            for (int i = 0; i < plot.getDataset().getColumnCount(); i++) {
                Number value = plot.getDataset().getValue(0, i);
                if (value != null) {
                    Color color = value.doubleValue() >= 0 ? SUCCESS_COLOR : DANGER_COLOR;
                    renderer.setSeriesPaint(i, color);
                }
            }
        } else {
            renderer.setSeriesPaint(0, PRIMARY_COLOR);
        }

        renderer.setDrawBarOutline(false);
        renderer.setShadowVisible(false);

        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setNumberFormatOverride(new DecimalFormat("#,###"));
    }

    private byte[] chartToBytes(JFreeChart chart, int width, int height) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ChartUtils.writeChartAsPNG(baos, chart, width, height);
            return baos.toByteArray();
        }
    }
}

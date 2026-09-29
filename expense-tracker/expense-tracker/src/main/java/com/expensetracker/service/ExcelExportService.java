package com.expensetracker.service;

import com.expensetracker.dto.CategorySummary;
import com.expensetracker.dto.DashboardSummary;
import com.expensetracker.model.Expense;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Builds a formatted .xlsx workbook for a given month:
 *  - "Expenses"          raw transaction list
 *  - "Category Summary"  spend vs budget per category + an embedded bar chart
 *  - "Daily Trend"       day-by-day spend + an embedded line chart
 *
 * The charts are native Excel chart objects (built with POI's XDDF chart API),
 * not pasted-in images, so they stay editable/interactive when opened in Excel.
 */
@Service
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public byte[] buildWorkbook(DashboardSummary summary, List<Expense> expenses) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle currencyStyle = currencyStyle(workbook);
            CellStyle titleStyle = titleStyle(workbook);

            buildExpenseSheet(workbook, expenses, headerStyle, currencyStyle, titleStyle);
            buildCategorySummarySheet(workbook, summary, headerStyle, currencyStyle, titleStyle);
            buildDailyTrendSheet(workbook, summary, headerStyle, currencyStyle, titleStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------------

    private void buildExpenseSheet(XSSFWorkbook wb, List<Expense> expenses,
                                    CellStyle headerStyle, CellStyle currencyStyle, CellStyle titleStyle) {
        XSSFSheet sheet = wb.createSheet("Expenses");
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("Expense Transactions");
        title.getCell(0).setCellStyle(titleStyle);

        String[] headers = {"Date", "Title", "Category", "Payment Method", "Amount", "Notes"};
        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 3;
        for (Expense e : expenses) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(e.getDate() != null ? e.getDate().format(DATE_FMT) : "");
            row.createCell(1).setCellValue(e.getTitle());
            row.createCell(2).setCellValue(e.getCategory() != null ? e.getCategory().getDisplayName() : "");
            row.createCell(3).setCellValue(e.getPaymentMethod() != null ? e.getPaymentMethod().name() : "");
            Cell amountCell = row.createCell(4);
            amountCell.setCellValue(e.getAmount() != null ? e.getAmount() : 0.0);
            amountCell.setCellStyle(currencyStyle);
            row.createCell(5).setCellValue(e.getNotes() != null ? e.getNotes() : "");
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildCategorySummarySheet(XSSFWorkbook wb, DashboardSummary summary,
                                            CellStyle headerStyle, CellStyle currencyStyle, CellStyle titleStyle) {
        XSSFSheet sheet = wb.createSheet("Category Summary");
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("Category Summary - " + summary.getYearMonth());
        title.getCell(0).setCellStyle(titleStyle);

        String[] headers = {"Category", "Spent", "Budget Limit", "% Used"};
        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        List<CategorySummary> categories = summary.getCategorySummaries();
        int rowIdx = 3;
        int firstDataRow = rowIdx;
        for (CategorySummary cs : categories) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(cs.getCategory().getDisplayName());
            Cell spentCell = row.createCell(1);
            spentCell.setCellValue(cs.getSpent());
            spentCell.setCellStyle(currencyStyle);
            Cell limitCell = row.createCell(2);
            limitCell.setCellValue(cs.getLimit());
            limitCell.setCellStyle(currencyStyle);
            row.createCell(3).setCellValue(Math.round(cs.getPercentUsed() * 10.0) / 10.0);
        }
        int lastDataRow = rowIdx - 1;

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        if (lastDataRow >= firstDataRow) {
            createBarChart(sheet, "Spend vs Budget by Category", firstDataRow, lastDataRow, lastDataRow + 3);
        }
    }

    private void buildDailyTrendSheet(XSSFWorkbook wb, DashboardSummary summary,
                                       CellStyle headerStyle, CellStyle currencyStyle, CellStyle titleStyle) {
        XSSFSheet sheet = wb.createSheet("Daily Trend");
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("Daily Spend - " + summary.getYearMonth());
        title.getCell(0).setCellStyle(titleStyle);

        Row headerRow = sheet.createRow(2);
        headerRow.createCell(0).setCellValue("Day");
        headerRow.createCell(1).setCellValue("Amount Spent");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);

        int rowIdx = 3;
        int firstDataRow = rowIdx;
        for (Map.Entry<String, Double> entry : summary.getDailySpend().entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            Cell amountCell = row.createCell(1);
            amountCell.setCellValue(entry.getValue());
            amountCell.setCellStyle(currencyStyle);
        }
        int lastDataRow = rowIdx - 1;

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);

        if (lastDataRow >= firstDataRow) {
            createLineChart(sheet, "Daily Spend Trend", firstDataRow, lastDataRow, lastDataRow + 3);
        }
    }

    // ---------------------------------------------------------------------
    // Native Excel charts via POI's XDDF chart API
    // ---------------------------------------------------------------------

    private void createBarChart(XSSFSheet sheet, String title, int firstRow, int lastRow, int anchorRow) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        var anchor = drawing.createAnchor(0, 0, 0, 0, 5, anchorRow, 13, anchorRow + 18);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(firstRow, lastRow, 0, 0));
        XDDFNumericalDataSource<Double> spent = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(firstRow, lastRow, 1, 1));
        XDDFNumericalDataSource<Double> limit = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(firstRow, lastRow, 2, 2));

        XDDFBarChartData barChart = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        barChart.setBarDirection(BarDirection.COL);

        XDDFBarChartData.Series series1 = (XDDFBarChartData.Series) barChart.addSeries(categories, spent);
        series1.setTitle("Spent", null);
        XDDFBarChartData.Series series2 = (XDDFBarChartData.Series) barChart.addSeries(categories, limit);
        series2.setTitle("Budget Limit", null);

        barChart.setBarDirection(BarDirection.COL);
        chart.plot(barChart);
    }

    private void createLineChart(XSSFSheet sheet, String title, int firstRow, int lastRow, int anchorRow) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        var anchor = drawing.createAnchor(0, 0, 0, 0, 4, anchorRow, 13, anchorRow + 18);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(firstRow, lastRow, 0, 0));
        XDDFNumericalDataSource<Double> amount = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(firstRow, lastRow, 1, 1));

        XDDFLineChartData lineChart = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        XDDFLineChartData.Series series = (XDDFLineChartData.Series) lineChart.addSeries(categories, amount);
        series.setTitle("Daily Spend", null);
        series.setSmooth(true);
        series.setMarkerStyle(MarkerStyle.CIRCLE);

        chart.plot(lineChart);
    }

    // ---------------------------------------------------------------------
    // Styles
    // ---------------------------------------------------------------------

    private CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x1D, (byte) 0x7A, (byte) 0x6F}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle currencyStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private CellStyle titleStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }
}

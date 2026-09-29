package com.expensetracker.controller;

import com.expensetracker.dto.DashboardSummary;
import com.expensetracker.model.Expense;
import com.expensetracker.service.ExcelExportService;
import com.expensetracker.service.ExpenseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExpenseService expenseService;
    private final ExcelExportService excelExportService;

    @Value("${app.export.directory:./exports}")
    private String exportDirectory;

    public ExportController(ExpenseService expenseService, ExcelExportService excelExportService) {
        this.expenseService = expenseService;
        this.excelExportService = excelExportService;
    }

    /**
     * Builds the workbook, saves a copy to disk under app.export.directory (so
     * every export is kept as a historical record), and streams it back to the
     * browser as a download.
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String month) throws IOException {
        String yearMonth = (month != null && !month.isBlank()) ? month : YearMonth.now().toString();
        DashboardSummary summary = expenseService.buildDashboardSummary(yearMonth);
        var expenses = expenseService.getForMonth(yearMonth);

        byte[] workbookBytes = excelExportService.buildWorkbook(summary, expenses);

        saveCopyToDisk(yearMonth, workbookBytes);

        String filename = "expense-report-" + yearMonth + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbookBytes);
    }

    private void saveCopyToDisk(String yearMonth, byte[] bytes) {
        try {
            Path dir = Path.of(exportDirectory);
            Files.createDirectories(dir);
            File file = dir.resolve("expense-report-" + yearMonth + ".xlsx").toFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }
        } catch (IOException e) {
            // Non-fatal: the download to the browser still succeeds even if the
            // on-disk archive copy fails (e.g. read-only filesystem).
            System.err.println("Could not save export copy to disk: " + e.getMessage());
        }
    }
}

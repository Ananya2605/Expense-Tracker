package com.expensetracker.controller;

import com.expensetracker.dto.AIAdviceResponse;
import com.expensetracker.dto.DashboardSummary;
import com.expensetracker.model.Category;
import com.expensetracker.service.AIAdvisorService;
import com.expensetracker.service.ExpenseService;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ExpenseService expenseService;
    private final AIAdvisorService aiAdvisorService;

    public DashboardController(ExpenseService expenseService, AIAdvisorService aiAdvisorService) {
        this.expenseService = expenseService;
        this.aiAdvisorService = aiAdvisorService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(required = false) String month) {
        String yearMonth = (month != null && !month.isBlank()) ? month : YearMonth.now().toString();
        return expenseService.buildDashboardSummary(yearMonth);
    }

    @GetMapping("/ai-advice")
    public AIAdviceResponse aiAdvice(@RequestParam(required = false) String month) {
        String yearMonth = (month != null && !month.isBlank()) ? month : YearMonth.now().toString();
        DashboardSummary summary = expenseService.buildDashboardSummary(yearMonth);
        return aiAdvisorService.analyze(summary);
    }

    @GetMapping("/categories")
    public Category[] categories() {
        return Category.values();
    }
}

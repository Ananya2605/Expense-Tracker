package com.expensetracker.controller;

import com.expensetracker.model.Category;
import com.expensetracker.model.MonthlyBudget;
import com.expensetracker.service.BudgetService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<MonthlyBudget> getForMonth(@RequestParam String month) {
        return budgetService.getForMonth(month);
    }

    @PostMapping("/overall")
    public MonthlyBudget setOverall(@RequestBody Map<String, Object> body) {
        String month = (String) body.get("yearMonth");
        double amount = Double.parseDouble(String.valueOf(body.get("amount")));
        return budgetService.setOverallBudget(month, amount);
    }

    @PostMapping("/category")
    public MonthlyBudget setCategory(@RequestBody Map<String, Object> body) {
        String month = (String) body.get("yearMonth");
        Category category = Category.valueOf(String.valueOf(body.get("category")));
        double amount = Double.parseDouble(String.valueOf(body.get("amount")));
        return budgetService.setCategoryBudget(month, category, amount);
    }
}

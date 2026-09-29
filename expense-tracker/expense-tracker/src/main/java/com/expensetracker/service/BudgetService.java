package com.expensetracker.service;

import com.expensetracker.model.Category;
import com.expensetracker.model.MonthlyBudget;
import com.expensetracker.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public List<MonthlyBudget> getForMonth(String yearMonth) {
        return budgetRepository.findByYearMonth(yearMonth);
    }

    public MonthlyBudget setOverallBudget(String yearMonth, double amount) {
        MonthlyBudget budget = budgetRepository.findByYearMonthAndCategoryIsNull(yearMonth)
                .orElse(new MonthlyBudget(yearMonth, null, amount));
        budget.setLimitAmount(amount);
        return budgetRepository.save(budget);
    }

    public MonthlyBudget setCategoryBudget(String yearMonth, Category category, double amount) {
        MonthlyBudget budget = budgetRepository.findByYearMonthAndCategory(yearMonth, category)
                .orElse(new MonthlyBudget(yearMonth, category, amount));
        budget.setLimitAmount(amount);
        return budgetRepository.save(budget);
    }
}

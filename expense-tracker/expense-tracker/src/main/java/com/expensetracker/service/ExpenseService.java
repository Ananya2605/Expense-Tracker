package com.expensetracker.service;

import com.expensetracker.dto.CategorySummary;
import com.expensetracker.dto.DashboardSummary;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.MonthlyBudget;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;

    public ExpenseService(ExpenseRepository expenseRepository, BudgetRepository budgetRepository) {
        this.expenseRepository = expenseRepository;
        this.budgetRepository = budgetRepository;
    }

    public List<Expense> getAll() {
        return expenseRepository.findAllByOrderByDateDesc();
    }

    public Expense getById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expense not found with id " + id));
    }

    public Expense create(Expense expense) {
        if (expense.getDate() == null) {
            expense.setDate(LocalDate.now());
        }
        return expenseRepository.save(expense);
    }

    public Expense update(Long id, Expense updated) {
        Expense existing = getById(id);
        existing.setTitle(updated.getTitle());
        existing.setAmount(updated.getAmount());
        existing.setCategory(updated.getCategory());
        existing.setDate(updated.getDate());
        existing.setNotes(updated.getNotes());
        existing.setPaymentMethod(updated.getPaymentMethod());
        return expenseRepository.save(existing);
    }

    public void delete(Long id) {
        expenseRepository.deleteById(id);
    }

    public List<Expense> getForMonth(String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        return expenseRepository.findByDateBetweenOrderByDateDesc(ym.atDay(1), ym.atEndOfMonth());
    }

    /**
     * Builds the full dashboard payload for a given month: totals, per-category
     * breakdown against budget limits, a daily trend map for the line chart,
     * and a simple linear projection of month-end spend based on the current
     * daily burn rate.
     */
    public DashboardSummary buildDashboardSummary(String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);
        List<Expense> monthExpenses = getForMonth(yearMonth);

        double totalSpent = monthExpenses.stream().mapToDouble(Expense::getAmount).sum();

        MonthlyBudget overallBudget = budgetRepository.findByYearMonthAndCategoryIsNull(yearMonth).orElse(null);
        double budgetLimit = overallBudget != null ? overallBudget.getLimitAmount() : 0.0;

        Map<Category, Double> spentByCategory = new EnumMap<>(Category.class);
        for (Expense e : monthExpenses) {
            spentByCategory.merge(e.getCategory(), e.getAmount(), Double::sum);
        }

        List<MonthlyBudget> categoryBudgets = budgetRepository.findByYearMonth(yearMonth);
        Map<Category, Double> limitByCategory = new EnumMap<>(Category.class);
        for (MonthlyBudget b : categoryBudgets) {
            if (b.getCategory() != null) {
                limitByCategory.put(b.getCategory(), b.getLimitAmount());
            }
        }

        List<CategorySummary> categorySummaries = new ArrayList<>();
        for (Category c : Category.values()) {
            double spent = spentByCategory.getOrDefault(c, 0.0);
            double limit = limitByCategory.getOrDefault(c, 0.0);
            if (spent > 0 || limit > 0) {
                categorySummaries.add(new CategorySummary(c, spent, limit));
            }
        }
        categorySummaries.sort((a, b) -> Double.compare(b.getSpent(), a.getSpent()));

        Map<String, Double> dailySpend = new LinkedHashMap<>();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd MMM");
        LocalDate cursor = ym.atDay(1);
        LocalDate today = LocalDate.now();
        LocalDate lastDayToShow = ym.equals(YearMonth.from(today)) ? today : ym.atEndOfMonth();
        Map<LocalDate, Double> byDate = new HashMap<>();
        for (Expense e : monthExpenses) {
            byDate.merge(e.getDate(), e.getAmount(), Double::sum);
        }
        while (!cursor.isAfter(lastDayToShow)) {
            dailySpend.put(cursor.format(dayFmt), byDate.getOrDefault(cursor, 0.0));
            cursor = cursor.plusDays(1);
        }

        int daysInMonth = ym.lengthOfMonth();
        int daysElapsed = ym.equals(YearMonth.from(today)) ? today.getDayOfMonth() : daysInMonth;
        double dailyAverage = daysElapsed > 0 ? totalSpent / daysElapsed : 0.0;
        double projected = dailyAverage * daysInMonth;

        DashboardSummary summary = new DashboardSummary();
        summary.setYearMonth(yearMonth);
        summary.setTotalSpent(round2(totalSpent));
        summary.setOverallBudget(round2(budgetLimit));
        summary.setRemaining(round2(budgetLimit - totalSpent));
        summary.setPercentUsed(budgetLimit > 0 ? round2((totalSpent / budgetLimit) * 100.0) : 0.0);
        summary.setProjectedMonthEndSpend(round2(projected));
        summary.setDaysElapsed(daysElapsed);
        summary.setDaysInMonth(daysInMonth);
        summary.setCategorySummaries(categorySummaries);
        summary.setDailySpend(dailySpend);
        return summary;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

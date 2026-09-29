package com.expensetracker.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * One row = the budget limit for a single category in a single month (yearMonth = "2026-09").
 * A row with category = null represents the OVERALL monthly budget limit.
 */
@Entity
@Table(name = "monthly_budgets", uniqueConstraints = @UniqueConstraint(columnNames = {"yearMonth", "category"}))
public class MonthlyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private String yearMonth; // format: yyyy-MM

    @Enumerated(EnumType.STRING)
    private Category category; // null = overall budget for the month

    @NotNull
    @Positive
    private Double limitAmount;

    public MonthlyBudget() {
    }

    public MonthlyBudget(String yearMonth, Category category, Double limitAmount) {
        this.yearMonth = yearMonth;
        this.category = category;
        this.limitAmount = limitAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Double getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(Double limitAmount) {
        this.limitAmount = limitAmount;
    }
}

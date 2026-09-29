package com.expensetracker.dto;

import com.expensetracker.model.Category;

public class CategorySummary {
    private Category category;
    private double spent;
    private double limit;
    private double percentUsed;

    public CategorySummary(Category category, double spent, double limit) {
        this.category = category;
        this.spent = spent;
        this.limit = limit;
        this.percentUsed = limit > 0 ? (spent / limit) * 100.0 : 0.0;
    }

    public Category getCategory() {
        return category;
    }

    public double getSpent() {
        return spent;
    }

    public double getLimit() {
        return limit;
    }

    public double getPercentUsed() {
        return percentUsed;
    }
}

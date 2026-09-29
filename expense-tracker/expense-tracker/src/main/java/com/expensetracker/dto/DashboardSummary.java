package com.expensetracker.dto;

import java.util.List;
import java.util.Map;

public class DashboardSummary {
    private String yearMonth;
    private double totalSpent;
    private double overallBudget;
    private double remaining;
    private double percentUsed;
    private double projectedMonthEndSpend;
    private int daysElapsed;
    private int daysInMonth;
    private List<CategorySummary> categorySummaries;
    private Map<String, Double> dailySpend; // date -> amount, for the trend line chart

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public double getOverallBudget() {
        return overallBudget;
    }

    public void setOverallBudget(double overallBudget) {
        this.overallBudget = overallBudget;
    }

    public double getRemaining() {
        return remaining;
    }

    public void setRemaining(double remaining) {
        this.remaining = remaining;
    }

    public double getPercentUsed() {
        return percentUsed;
    }

    public void setPercentUsed(double percentUsed) {
        this.percentUsed = percentUsed;
    }

    public double getProjectedMonthEndSpend() {
        return projectedMonthEndSpend;
    }

    public void setProjectedMonthEndSpend(double projectedMonthEndSpend) {
        this.projectedMonthEndSpend = projectedMonthEndSpend;
    }

    public int getDaysElapsed() {
        return daysElapsed;
    }

    public void setDaysElapsed(int daysElapsed) {
        this.daysElapsed = daysElapsed;
    }

    public int getDaysInMonth() {
        return daysInMonth;
    }

    public void setDaysInMonth(int daysInMonth) {
        this.daysInMonth = daysInMonth;
    }

    public List<CategorySummary> getCategorySummaries() {
        return categorySummaries;
    }

    public void setCategorySummaries(List<CategorySummary> categorySummaries) {
        this.categorySummaries = categorySummaries;
    }

    public Map<String, Double> getDailySpend() {
        return dailySpend;
    }

    public void setDailySpend(Map<String, Double> dailySpend) {
        this.dailySpend = dailySpend;
    }
}

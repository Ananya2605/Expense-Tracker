package com.expensetracker.dto;

public class AISuggestion {
    private String title;
    private String detail;
    private String priority;      // "high", "medium", "low"
    private Double impactAmount;  // estimated ₹ this suggestion could save, null if not quantifiable
    private String icon;          // a short label used to pick a frontend icon

    public AISuggestion() {
    }

    public AISuggestion(String title, String detail, String priority, Double impactAmount, String icon) {
        this.title = title;
        this.detail = detail;
        this.priority = priority;
        this.impactAmount = impactAmount;
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Double getImpactAmount() {
        return impactAmount;
    }

    public void setImpactAmount(Double impactAmount) {
        this.impactAmount = impactAmount;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}

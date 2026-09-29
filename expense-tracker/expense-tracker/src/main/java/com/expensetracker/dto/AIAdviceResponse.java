package com.expensetracker.dto;

import java.util.List;

public class AIAdviceResponse {
    private String status;          // "ON_TRACK", "AT_RISK", "OVER_BUDGET", "NO_BUDGET"
    private String headline;        // one-line verdict
    private List<AISuggestion> suggestions;
    private Double projectedOverspend; // null if not overspending
    private int healthScore;        // 0-100 overall budget health score

    public AIAdviceResponse() {
    }

    public AIAdviceResponse(String status, String headline, List<AISuggestion> suggestions,
                             Double projectedOverspend, int healthScore) {
        this.status = status;
        this.headline = headline;
        this.suggestions = suggestions;
        this.projectedOverspend = projectedOverspend;
        this.healthScore = healthScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public List<AISuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<AISuggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public Double getProjectedOverspend() {
        return projectedOverspend;
    }

    public void setProjectedOverspend(Double projectedOverspend) {
        this.projectedOverspend = projectedOverspend;
    }

    public int getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(int healthScore) {
        this.healthScore = healthScore;
    }
}

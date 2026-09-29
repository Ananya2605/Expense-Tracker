package com.expensetracker.service;

import com.expensetracker.dto.AIAdviceResponse;
import com.expensetracker.dto.AISuggestion;
import com.expensetracker.dto.CategorySummary;
import com.expensetracker.dto.DashboardSummary;
import com.expensetracker.model.Category;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A rule-based "budget advisor" engine. It looks at the current month's spend
 * pattern (total burn rate, category mix, projection to month-end) and turns
 * that into structured, prioritized recommendations - the same reasoning a
 * personal finance coach would apply, expressed as deterministic rules so the
 * advice is explainable and doesn't depend on an external AI service.
 *
 * Categories are ranked by how "discretionary" they typically are, so the
 * advisor suggests trimming eating out / entertainment / shopping before it
 * ever suggests trimming rent or health spending. Each suggestion also carries
 * a priority tag and, where it can be computed, an estimated rupee impact -
 * the numbers the UI uses to render recommendation cards.
 */
@Service
public class AIAdvisorService {

    private static final int DISCRETIONARY_SCORE_DEFAULT = 5;

    public AIAdviceResponse analyze(DashboardSummary summary) {
        List<AISuggestion> suggestions = new ArrayList<>();
        String status;
        String headline;
        Double projectedOverspend = null;

        boolean hasBudget = summary.getOverallBudget() > 0;

        if (!hasBudget) {
            suggestions.add(new AISuggestion(
                    "Set your first budget",
                    "Go to Budget and set an overall monthly limit, plus limits for your top 3-4 categories, so I can start tracking your spending health.",
                    "high", null, "target"));
            suggestions.add(new AISuggestion(
                    "Try the 50/30/20 rule",
                    "A common starting point: 50% of income on needs, 30% on wants, 20% on savings - then adjust to fit your life.",
                    "medium", null, "lightbulb"));
            return new AIAdviceResponse("NO_BUDGET",
                    "Set a monthly budget so I can start tracking your spending health.",
                    suggestions, null, 0);
        }

        double percentUsed = summary.getPercentUsed();
        double projected = summary.getProjectedMonthEndSpend();
        double budget = summary.getOverallBudget();
        int daysLeft = Math.max(summary.getDaysInMonth() - summary.getDaysElapsed(), 0);

        // ---- Overall verdict -------------------------------------------------
        if (projected > budget) {
            projectedOverspend = round2(projected - budget);
            if (percentUsed >= 100) {
                status = "OVER_BUDGET";
                headline = String.format(
                        "You've already spent %.0f%% of this month's budget with %d day%s left. Time to pull back.",
                        percentUsed, daysLeft, daysLeft == 1 ? "" : "s");
            } else {
                status = "AT_RISK";
                headline = String.format(
                        "At your current pace you'll finish the month ₹%.0f over budget - here's how to fix that.",
                        projectedOverspend);
            }
        } else {
            status = "ON_TRACK";
            headline = String.format(
                    "You're on track: %.0f%% of budget used with %d day%s left in the month.",
                    percentUsed, daysLeft, daysLeft == 1 ? "" : "s");
        }

        // ---- Category-level suggestions --------------------------------------
        List<CategorySummary> overLimitCats = new ArrayList<>();
        List<CategorySummary> nearLimitCats = new ArrayList<>();
        for (CategorySummary cs : summary.getCategorySummaries()) {
            if (cs.getLimit() <= 0) continue;
            if (cs.getPercentUsed() >= 100) {
                overLimitCats.add(cs);
            } else if (cs.getPercentUsed() >= 80) {
                nearLimitCats.add(cs);
            }
        }

        overLimitCats.sort(Comparator.comparingInt((CategorySummary cs) -> discretionaryScore(cs.getCategory())));
        for (CategorySummary cs : overLimitCats) {
            double over = cs.getSpent() - cs.getLimit();
            suggestions.add(new AISuggestion(
                    "Rein in " + cs.getCategory().getDisplayName(),
                    String.format("This category is %.0f%% over its limit. Try capping it at ₹%.0f/week for the rest of the month.",
                            cs.getPercentUsed() - 100, Math.max(cs.getLimit() / 4.0, 0)),
                    "high", round2(over), categoryIcon(cs.getCategory())));
        }

        nearLimitCats.sort(Comparator.comparingInt((CategorySummary cs) -> discretionaryScore(cs.getCategory())));
        for (CategorySummary cs : nearLimitCats) {
            suggestions.add(new AISuggestion(
                    "Watch " + cs.getCategory().getDisplayName(),
                    String.format("You're at %.0f%% of this category's limit - slow down here to avoid going over.", cs.getPercentUsed()),
                    "medium", null, categoryIcon(cs.getCategory())));
        }

        if ((status.equals("OVER_BUDGET") || status.equals("AT_RISK")) && overLimitCats.isEmpty() && nearLimitCats.isEmpty()) {
            summary.getCategorySummaries().stream()
                    .filter(cs -> discretionaryScore(cs.getCategory()) <= 4)
                    .sorted((a, b) -> Double.compare(b.getSpent(), a.getSpent()))
                    .limit(3)
                    .forEach(cs -> {
                        double cutBy = cs.getSpent() * 0.15;
                        suggestions.add(new AISuggestion(
                                "Trim " + cs.getCategory().getDisplayName(),
                                String.format("Your biggest flexible spend so far (₹%.0f). Cutting it by ~15%% would meaningfully help your budget.", cs.getSpent()),
                                "high", round2(cutBy), categoryIcon(cs.getCategory())));
                    });
        }

        if (status.equals("ON_TRACK")) {
            suggestions.add(new AISuggestion(
                    "Move money to savings now",
                    "You're under budget - transfer a fixed amount to Savings & Investment today rather than waiting for month-end leftovers.",
                    "low", null, "piggy-bank"));
        }

        if (status.equals("OVER_BUDGET") && daysLeft > 0) {
            double remainingBudget = Math.max(budget - summary.getTotalSpent(), 0);
            double dailyAllowance = remainingBudget / daysLeft;
            suggestions.add(new AISuggestion(
                    "Set a daily spending cap",
                    String.format("Keep spending under ₹%.0f/day for the remaining %d day%s to land back on budget.",
                            dailyAllowance, daysLeft, daysLeft == 1 ? "" : "s"),
                    "high", null, "calendar"));
        }

        if (suggestions.isEmpty()) {
            suggestions.add(new AISuggestion("No red flags this month", "Your spending pattern looks healthy - keep it up.", "low", null, "check"));
        }

        // Sort so high-priority cards render first.
        suggestions.sort(Comparator.comparingInt(s -> priorityRank(s.getPriority())));

        int healthScore = computeHealthScore(status, percentUsed, overLimitCats.size(), nearLimitCats.size());

        return new AIAdviceResponse(status, headline, suggestions, projectedOverspend, healthScore);
    }

    private int computeHealthScore(String status, double percentUsed, int overCount, int nearCount) {
        double score = 100;
        score -= Math.max(0, percentUsed - 50) * 0.6; // gently penalize burn rate above 50%
        score -= overCount * 12;
        score -= nearCount * 5;
        if (status.equals("OVER_BUDGET")) score -= 15;
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private int discretionaryScore(Category category) {
        return switch (category) {
            case ENTERTAINMENT -> 1;
            case SHOPPING -> 2;
            case FOOD -> 3;
            case TRAVEL -> 4;
            case TRANSPORT -> 6;
            case UTILITIES -> 8;
            case EDUCATION -> 8;
            case HEALTH -> 9;
            case HOUSING -> 10;
            case SAVINGS_INVESTMENT -> 10;
            default -> DISCRETIONARY_SCORE_DEFAULT;
        };
    }

    private String categoryIcon(Category category) {
        return switch (category) {
            case FOOD -> "utensils";
            case TRANSPORT -> "car";
            case HOUSING -> "home";
            case UTILITIES -> "bolt";
            case ENTERTAINMENT -> "film";
            case HEALTH -> "heart";
            case SHOPPING -> "bag";
            case EDUCATION -> "book";
            case TRAVEL -> "plane";
            case SAVINGS_INVESTMENT -> "piggy-bank";
            default -> "tag";
        };
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

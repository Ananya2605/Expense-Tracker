package com.expensetracker.model;

public enum Category {
    FOOD("Food & Dining"),
    TRANSPORT("Transport"),
    HOUSING("Housing & Rent"),
    UTILITIES("Utilities"),
    ENTERTAINMENT("Entertainment"),
    HEALTH("Health & Fitness"),
    SHOPPING("Shopping"),
    EDUCATION("Education"),
    TRAVEL("Travel"),
    SAVINGS_INVESTMENT("Savings & Investment"),
    OTHER("Other");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

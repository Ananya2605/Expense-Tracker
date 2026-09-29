# Ledger — Expense Tracker

A full-stack personal expense tracker: **Spring Boot 3 (Java 17)** on the backend,
plain **HTML/CSS/JS** (Chart.js) on the frontend. Includes a rule-based AI budget
advisor, dashboard charts, and one-click Excel export with native embedded charts.

## Features

- **Add / edit / delete expenses** with category, payment method, notes
- **Monthly budgets** — an overall limit plus per-category limits
- **Dashboard** — total spent, remaining, % of budget used, projected month-end
  spend (based on your current daily burn rate), a daily trend line chart and a
  category breakdown doughnut chart
- **AI Budget Advisor** — a rule-based analysis engine that:
  - flags categories that are over or near their limit
  - ranks suggestions by how "cuttable" a category typically is (e.g. suggests
    trimming entertainment/shopping before rent or health)
  - projects whether you'll finish the month over budget, and by how much
  - tells you a safe daily spending allowance to get back on track
- **Excel export** (`.xlsx`, via Apache POI) — every export contains:
  - a full transaction list
  - a category-summary sheet with a native embedded bar chart (spend vs. budget)
  - a daily-trend sheet with a native embedded line chart
  - a dated copy is also archived automatically under `./exports/`
- **Dark / light mode**, persisted in the browser, professional ink-and-teal
  finance-dashboard theme

## Tech stack

| Layer     | Tech                                                            |
|-----------|------------------------------------------------------------------|
| Backend   | Spring Boot 3.3, Spring Data JPA, Bean Validation                |
| Database  | H2 (file-based, `./data/`) — zero setup, swap for MySQL/Postgres later |
| Excel     | Apache POI 5.2 (XSSF/XDDF — native, editable Excel charts)       |
| Frontend  | HTML5, CSS3 (custom design system, CSS variables), vanilla JS    |
| Charts    | Chart.js 4 (loaded via CDN)                                      |

## Project structure

```
expense-tracker/
├── pom.xml
├── src/main/java/com/expensetracker/
│   ├── ExpenseTrackerApplication.java
│   ├── model/            Expense, Category, PaymentMethod, MonthlyBudget
│   ├── repository/       ExpenseRepository, BudgetRepository (Spring Data JPA)
│   ├── service/          ExpenseService, BudgetService, AIAdvisorService, ExcelExportService
│   ├── controller/       ExpenseController, BudgetController, DashboardController, ExportController
│   ├── dto/               CategorySummary, DashboardSummary, AIAdviceResponse
│   └── config/            CorsConfig, GlobalExceptionHandler
└── src/main/resources/
    ├── application.properties
    └── static/            index.html, css/style.css, js/app.js
```

## Running it

**Prerequisites:** Java 17+, Maven 3.8+ (internet access on first build, to pull dependencies).

```bash
cd expense-tracker
mvn spring-boot:run
```

Then open **http://localhost:8080** in your browser. That's it — the H2 database
file is created automatically under `./data/` the first time you run it, so
your expenses and budgets persist across restarts.

To build a standalone jar instead:

```bash
mvn clean package
java -jar target/expense-tracker.jar
```

### Optional: inspect the database directly

Visit `http://localhost:8080/h2-console` while the app is running.
JDBC URL: `jdbc:h2:file:./data/expensetracker`, user `sa`, blank password.

## How the AI advisor works

There's no external AI API key required — `AIAdvisorService` is a small,
transparent **rule engine**: it compares your spend pace against your budget,
projects your month-end total from the current daily average, and ranks
category-level suggestions by a "how discretionary is this?" score (rent and
health are protected; dining out, shopping and entertainment get suggested
first). This keeps the advice fast, free, explainable, and fully offline.

If you'd like to swap in a real LLM for richer natural-language suggestions,
the natural place to do it is inside `AIAdvisorService.analyze()` — replace
the rule logic with a call to your preferred AI API, passing it the
`DashboardSummary` as structured context.

## Notes & next steps

- Swap H2 for MySQL/Postgres by changing the four `spring.datasource.*` lines
  in `application.properties` and adding the relevant driver to `pom.xml`.
- Add authentication (Spring Security) before deploying anywhere multi-user —
  right now all data is shared/global, there's no per-user login.
- The category list is a fixed enum (`Category.java`); add more categories
  there if needed.

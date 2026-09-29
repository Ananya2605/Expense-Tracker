package com.expensetracker.repository;

import com.expensetracker.model.Category;
import com.expensetracker.model.MonthlyBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<MonthlyBudget, Long> {

    List<MonthlyBudget> findByYearMonth(String yearMonth);

    Optional<MonthlyBudget> findByYearMonthAndCategoryIsNull(String yearMonth);

    Optional<MonthlyBudget> findByYearMonthAndCategory(String yearMonth, Category category);
}

package com.expensetracker.repository;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Used to page/filter expenses for a given month: service computes the
    // first and last day of the month and passes them here.
    List<Expense> findByDateBetweenOrderByDateDesc(LocalDate start, LocalDate end);

    List<Expense> findByCategoryAndDateBetween(Category category, LocalDate start, LocalDate end);

    List<Expense> findAllByOrderByDateDesc();
}

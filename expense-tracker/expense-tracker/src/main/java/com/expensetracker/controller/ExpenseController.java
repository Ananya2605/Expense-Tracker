package com.expensetracker.controller;

import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<Expense> getAll(@RequestParam(required = false) String month) {
        if (month != null && !month.isBlank()) {
            return expenseService.getForMonth(month);
        }
        return expenseService.getAll();
    }

    @GetMapping("/{id}")
    public Expense getOne(@PathVariable Long id) {
        return expenseService.getById(id);
    }

    @PostMapping
    public ResponseEntity<Expense> create(@Valid @RequestBody Expense expense) {
        Expense saved = expenseService.create(expense);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public Expense update(@PathVariable Long id, @Valid @RequestBody Expense expense) {
        return expenseService.update(id, expense);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.expensetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExpenseTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
        System.out.println("\n=========================================");
        System.out.println(" Expense Tracker is running:");
        System.out.println(" App:      http://localhost:8080");
        System.out.println(" H2 DB UI: http://localhost:8080/h2-console");
        System.out.println("=========================================\n");
    }
}

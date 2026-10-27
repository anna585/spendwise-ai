package com.example.spendwise.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.spendwise.entity.Category;
import com.example.spendwise.entity.TransactionType;
import com.example.spendwise.service.BudgetReportRow;
import com.example.spendwise.service.CategoryService;
import com.example.spendwise.service.DuplicateMonthlyBudgetException;
import com.example.spendwise.service.MonthlyBudgetNotFoundException;
import com.example.spendwise.service.MonthlyBudgetService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MonthlyBudgetController.class)
class MonthlyBudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthlyBudgetService budgetService;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void defaultsReportToCurrentMonthAndDisplaysCategorySpending() throws Exception {
        when(budgetService.getReport(YearMonth.now())).thenReturn(List.of(
                new BudgetReportRow(1L, "Dining", 2L, new BigDecimal("300.00"),
                        new BigDecimal("325.75"), new BigDecimal("-25.75")),
                new BudgetReportRow(2L, "Travel", null, null,
                        new BigDecimal("0.00"), null)
        ));

        mockMvc.perform(get("/budgets"))
                .andExpect(status().isOk())
                .andExpect(view().name("budgets/list"))
                .andExpect(model().attribute("selectedMonth", YearMonth.now()))
                .andExpect(model().attributeExists("reportRows"));
    }

    @Test
    void createsBudgetAndReturnsToSelectedMonth() throws Exception {
        mockMvc.perform(post("/budgets")
                        .param("month", "2026-10")
                        .param("amount", "300.00")
                        .param("categoryId", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budgets?month=2026-10"));

        verify(budgetService).create(YearMonth.of(2026, 10), new BigDecimal("300.00"), 4L);
    }

    @Test
    void validatesInvalidBudgetAmountsWithoutCallingService() throws Exception {
        mockMvc.perform(post("/budgets")
                        .param("month", "2026-10")
                        .param("amount", "0")
                        .param("categoryId", "4"))
                .andExpect(status().isOk())
                .andExpect(view().name("budgets/form"))
                .andExpect(model().attributeHasFieldErrors("budgetForm", "amount"));
    }

    @Test
    void rejectsNegativeBudgetAmountWithoutCallingService() throws Exception {
        mockMvc.perform(post("/budgets")
                        .param("month", "2026-10")
                        .param("amount", "-25.00")
                        .param("categoryId", "4"))
                .andExpect(status().isOk())
                .andExpect(view().name("budgets/form"))
                .andExpect(model().attributeHasFieldErrors("budgetForm", "amount"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void displaysDuplicateCategoryMonthMessageOnBudgetForm() throws Exception {
        when(categoryService.findAllByType(TransactionType.EXPENSE))
                .thenReturn(List.of(new Category("Dining", TransactionType.EXPENSE)));
        doThrow(new DuplicateMonthlyBudgetException("Dining", YearMonth.of(2026, 10)))
                .when(budgetService)
                .create(YearMonth.of(2026, 10), new BigDecimal("300.00"), 4L);

        mockMvc.perform(post("/budgets")
                        .param("month", "2026-10")
                        .param("amount", "300.00")
                        .param("categoryId", "4"))
                .andExpect(status().isOk())
                .andExpect(view().name("budgets/form"))
                .andExpect(model().attributeHasFieldErrors("budgetForm", "categoryId"));
    }

    @Test
    void updatesAndDeletesBudgetWithoutChangingSelectedMonth() throws Exception {
        mockMvc.perform(post("/budgets/8")
                        .param("month", "2026-10")
                        .param("amount", "450.00")
                        .param("categoryId", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budgets?month=2026-10"));
        verify(budgetService).update(8L, YearMonth.of(2026, 10), new BigDecimal("450.00"), 4L);

        mockMvc.perform(post("/budgets/8/delete").param("month", "2026-10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budgets?month=2026-10"));
        verify(budgetService).delete(8L);
    }

    @Test
    void returnsNotFoundPageWhenEditingMissingBudget() throws Exception {
        when(budgetService.findById(404L))
                .thenThrow(new MonthlyBudgetNotFoundException(404L));

        mockMvc.perform(get("/budgets/404/edit"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("budgets/not-found"))
                .andExpect(model().attributeExists("errorMessage"));
    }
}

package com.example.spendwise.controller;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
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
import com.example.spendwise.entity.Transaction;
import com.example.spendwise.entity.TransactionType;
import com.example.spendwise.service.CategoryNotFoundException;
import com.example.spendwise.service.CategoryService;
import com.example.spendwise.service.TransactionCategoryTypeMismatchException;
import com.example.spendwise.service.TransactionNotFoundException;
import com.example.spendwise.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void listsTransactions() throws Exception {
        when(transactionService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/list"))
                .andExpect(model().attributeExists("transactions"));
    }

    @Test
    void rendersCreateFormWithCategoriesAndTypes() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of(category()));

        mockMvc.perform(get("/transactions/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/form"))
                .andExpect(model().attributeExists(
                        "transactionForm",
                        "categories",
                        "transactionTypes",
                        "formAction"
                ));
    }

    @Test
    void createsTransactionAndRedirects() throws Exception {
        when(transactionService.create(
                TransactionType.EXPENSE,
                new BigDecimal("12.50"),
                LocalDate.of(2026, 10, 16),
                "Lunch",
                3L
        )).thenReturn(transaction());

        mockMvc.perform(post("/transactions")
                        .param("type", "EXPENSE")
                        .param("amount", "12.50")
                        .param("transactionDate", "2026-10-16")
                        .param("description", "Lunch")
                        .param("categoryId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"));

        verify(transactionService).create(
                TransactionType.EXPENSE,
                new BigDecimal("12.50"),
                LocalDate.of(2026, 10, 16),
                "Lunch",
                3L
        );
    }

    @Test
    void preservesFormValuesWhenValidationFails() throws Exception {
        mockMvc.perform(post("/transactions")
                        .param("type", "EXPENSE")
                        .param("amount", "0")
                        .param("transactionDate", "2026-10-16")
                        .param("description", "Lunch")
                        .param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/form"))
                .andExpect(model().attributeHasFieldErrors("transactionForm", "amount"))
                .andExpect(model().attributeExists("categories", "transactionTypes"));
    }

    @Test
    void rejectsNegativeTransactionAmountWithoutCallingService() throws Exception {
        mockMvc.perform(post("/transactions")
                        .param("type", "EXPENSE")
                        .param("amount", "-4.00")
                        .param("transactionDate", "2026-10-16")
                        .param("description", "Lunch")
                        .param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/form"))
                .andExpect(model().attributeHasFieldErrors("transactionForm", "amount"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void reportsCategoryTypeMismatchWithoutLosingSubmittedForm() throws Exception {
        doThrow(new TransactionCategoryTypeMismatchException(TransactionType.EXPENSE))
                .when(transactionService)
                .create(
                        TransactionType.EXPENSE,
                        new BigDecimal("12.50"),
                        LocalDate.of(2026, 10, 16),
                        "Lunch",
                        3L
                );
        when(categoryService.findAll()).thenReturn(List.of(category()));

        mockMvc.perform(post("/transactions")
                        .param("type", "EXPENSE")
                        .param("amount", "12.50")
                        .param("transactionDate", "2026-10-16")
                        .param("description", "Lunch")
                        .param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/form"))
                .andExpect(model().attributeHasFieldErrors("transactionForm", "categoryId"))
                .andExpect(model().attributeExists("categories", "transactionTypes"));
    }

    @Test
    void reportsMissingCategoryOnForm() throws Exception {
        doThrow(new CategoryNotFoundException(404L))
                .when(transactionService)
                .create(
                        TransactionType.EXPENSE,
                        new BigDecimal("12.50"),
                        LocalDate.of(2026, 10, 16),
                        "Lunch",
                        404L
                );
        when(categoryService.findAll()).thenReturn(List.of(category()));

        mockMvc.perform(post("/transactions")
                        .param("type", "EXPENSE")
                        .param("amount", "12.50")
                        .param("transactionDate", "2026-10-16")
                        .param("description", "Lunch")
                        .param("categoryId", "404"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/form"))
                .andExpect(model().attributeHasFieldErrors("transactionForm", "categoryId"));
    }

    @Test
    void populatesEditFormFromExistingTransaction() throws Exception {
        when(transactionService.findById(8L)).thenReturn(transaction());
        when(categoryService.findAll()).thenReturn(List.of(category()));

        mockMvc.perform(get("/transactions/8/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/form"))
                .andExpect(model().attribute("transactionId", 8L))
                .andExpect(model().attributeExists("transactionForm", "formAction"));
    }

    @Test
    void updatesTransactionAndRedirects() throws Exception {
        when(transactionService.update(
                8L,
                TransactionType.EXPENSE,
                new BigDecimal("12.50"),
                LocalDate.of(2026, 10, 16),
                "Lunch",
                3L
        )).thenReturn(transaction());

        mockMvc.perform(post("/transactions/8")
                        .param("type", "EXPENSE")
                        .param("amount", "12.50")
                        .param("transactionDate", "2026-10-16")
                        .param("description", "Lunch")
                        .param("categoryId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"));
    }

    @Test
    void preservesEditStateAndSubmittedValuesWhenUpdateValidationFails() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of(category()));

        mockMvc.perform(post("/transactions/8")
                        .param("type", "EXPENSE")
                        .param("amount", "-4.00")
                        .param("transactionDate", "2026-10-16")
                        .param("description", "Updated lunch")
                        .param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/form"))
                .andExpect(model().attribute("transactionId", 8L))
                .andExpect(model().attributeExists("formAction", "pageTitle", "categories"))
                .andExpect(model().attributeHasFieldErrors("transactionForm", "amount"))
                .andExpect(model().attribute(
                        "transactionForm",
                        hasProperty("description", is("Updated lunch"))
                ));
    }

    @Test
    void confirmsDeleteWithASeparatePage() throws Exception {
        when(transactionService.findById(8L)).thenReturn(transaction());

        mockMvc.perform(get("/transactions/8/delete"))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions/delete"))
                .andExpect(model().attributeExists("transaction"));
    }

    @Test
    void deletesOnlyThroughPostAndRedirects() throws Exception {
        doNothing().when(transactionService).delete(8L);

        mockMvc.perform(post("/transactions/8/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions"));

        verify(transactionService).delete(8L);
    }

    @Test
    void returnsNotFoundPageForMissingTransaction() throws Exception {
        when(transactionService.findById(404L))
                .thenThrow(new TransactionNotFoundException(404L));

        mockMvc.perform(get("/transactions/404/edit"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("transactions/not-found"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void returnsNotFoundPageWhenDeletingMissingTransaction() throws Exception {
        doThrow(new TransactionNotFoundException(404L))
                .when(transactionService)
                .delete(404L);

        mockMvc.perform(post("/transactions/404/delete"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("transactions/not-found"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    private Category category() {
        return new Category("Dining", TransactionType.EXPENSE);
    }

    private Transaction transaction() {
        return new Transaction(
                TransactionType.EXPENSE,
                new BigDecimal("12.50"),
                LocalDate.of(2026, 10, 16),
                "Lunch",
                category()
        );
    }
}

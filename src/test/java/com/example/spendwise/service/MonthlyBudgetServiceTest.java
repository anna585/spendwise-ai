package com.example.spendwise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.spendwise.entity.Category;
import com.example.spendwise.entity.MonthlyBudget;
import com.example.spendwise.entity.TransactionType;
import com.example.spendwise.repository.CategoryExpenseTotal;
import com.example.spendwise.repository.CategoryRepository;
import com.example.spendwise.repository.MonthlyBudgetRepository;
import com.example.spendwise.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyBudgetServiceTest {

    @Mock
    private MonthlyBudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryExpenseTotal expenseTotal;

    private MonthlyBudgetService budgetService;
    private Category expenseCategory;

    @BeforeEach
    void setUp() {
        budgetService = new MonthlyBudgetService(
                budgetRepository,
                categoryRepository,
                transactionRepository
        );
        expenseCategory = new Category("Dining", TransactionType.EXPENSE);
    }

    @Test
    void createsBudgetForAnExpenseCategoryAndNormalizesMonth() {
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(expenseCategory));
        when(budgetRepository.existsByCategory_IdAndMonth(4L, LocalDate.of(2026, 10, 1)))
                .thenReturn(false);
        when(budgetRepository.save(any(MonthlyBudget.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MonthlyBudget created = budgetService.create(
                YearMonth.of(2026, 10),
                new BigDecimal("300.00"),
                4L
        );

        assertThat(created.getMonth()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(created.getAmount()).isEqualByComparingTo("300.00");
        assertThat(created.getCategory()).isSameAs(expenseCategory);
    }

    @Test
    void rejectsDuplicateBudgetForCategoryAndMonth() {
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(expenseCategory));
        when(budgetRepository.existsByCategory_IdAndMonth(4L, LocalDate.of(2026, 10, 1)))
                .thenReturn(true);

        assertThatThrownBy(() -> budgetService.create(
                YearMonth.of(2026, 10),
                new BigDecimal("300.00"),
                4L
        )).isInstanceOf(DuplicateMonthlyBudgetException.class)
                .hasMessageContaining("Dining")
                .hasMessageContaining("2026-10");

        verify(budgetRepository, never()).save(any(MonthlyBudget.class));
    }

    @Test
    void rejectsBudgetForMissingCategory() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.create(
                YearMonth.of(2026, 10),
                new BigDecimal("300.00"),
                404L
        )).isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("404");

        verify(budgetRepository, never()).existsByCategory_IdAndMonth(
                404L, LocalDate.of(2026, 10, 1)
        );
        verify(budgetRepository, never()).save(any(MonthlyBudget.class));
    }

    @Test
    void rejectsBudgetForIncomeCategory() {
        Category incomeCategory = mock(Category.class);
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> budgetService.create(
                YearMonth.of(2026, 10),
                new BigDecimal("300.00"),
                9L
        )).isInstanceOf(InvalidBudgetCategoryException.class);

        verify(budgetRepository, never()).save(any(MonthlyBudget.class));
    }

    @Test
    void updatesBudgetAndAllowsItsExistingCategoryAndMonth() {
        MonthlyBudget existing = new MonthlyBudget(
                LocalDate.of(2026, 9, 1),
                new BigDecimal("250.00"),
                expenseCategory
        );
        when(budgetRepository.findByIdWithCategory(12L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(expenseCategory));
        when(budgetRepository.existsByCategory_IdAndMonthAndIdNot(
                4L, LocalDate.of(2026, 10, 1), 12L
        )).thenReturn(false);
        when(budgetRepository.save(existing)).thenReturn(existing);

        MonthlyBudget updated = budgetService.update(
                12L, YearMonth.of(2026, 10), new BigDecimal("400.00"), 4L
        );

        assertThat(updated.getMonth()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(updated.getAmount()).isEqualByComparingTo("400.00");
        verify(budgetRepository).save(existing);
    }

    @Test
    void rejectsUpdateWhenBudgetDoesNotExist() {
        when(budgetRepository.findByIdWithCategory(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.update(
                404L, YearMonth.of(2026, 10), new BigDecimal("400.00"), 4L
        )).isInstanceOf(MonthlyBudgetNotFoundException.class)
                .hasMessageContaining("404");

        verify(categoryRepository, never()).findById(4L);
        verify(budgetRepository, never()).save(any(MonthlyBudget.class));
    }

    @Test
    void rejectsUpdateWhenAnotherBudgetAlreadyUsesCategoryAndMonth() {
        MonthlyBudget existing = new MonthlyBudget(
                LocalDate.of(2026, 9, 1),
                new BigDecimal("250.00"),
                expenseCategory
        );
        when(budgetRepository.findByIdWithCategory(12L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(expenseCategory));
        when(budgetRepository.existsByCategory_IdAndMonthAndIdNot(
                4L, LocalDate.of(2026, 10, 1), 12L
        )).thenReturn(true);

        assertThatThrownBy(() -> budgetService.update(
                12L, YearMonth.of(2026, 10), new BigDecimal("400.00"), 4L
        )).isInstanceOf(DuplicateMonthlyBudgetException.class);

        verify(budgetRepository, never()).save(existing);
    }

    @Test
    void deletingBudgetDoesNotDeleteTransactions() {
        MonthlyBudget existing = new MonthlyBudget(
                LocalDate.of(2026, 10, 1),
                new BigDecimal("250.00"),
                expenseCategory
        );
        when(budgetRepository.findByIdWithCategory(12L)).thenReturn(Optional.of(existing));

        budgetService.delete(12L);

        verify(budgetRepository).delete(existing);
        verify(transactionRepository, never()).delete(any());
    }

    @Test
    void reportsSpentAndRemainingForSelectedMonth() {
        Category reportCategory = reportCategory();
        MonthlyBudget budget = new MonthlyBudget(
                LocalDate.of(2026, 10, 1),
                new BigDecimal("300.00"),
                reportCategory
        );
        when(budgetRepository.findAllByMonthOrderByCategory_NameAsc(LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of(budget));
        when(categoryRepository.findAllByTypeOrderByNameAsc(TransactionType.EXPENSE))
                .thenReturn(List.of(reportCategory));
        when(transactionRepository.sumExpensesByCategoryAndDateRange(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)
        )).thenReturn(List.of(expenseTotal));
        when(expenseTotal.getCategoryId()).thenReturn(4L);
        when(expenseTotal.getAmount()).thenReturn(new BigDecimal("325.75"));

        List<BudgetReportRow> rows = budgetService.getReport(YearMonth.of(2026, 10));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).amountSpent()).isEqualByComparingTo("325.75");
        assertThat(rows.get(0).remainingAmount()).isEqualByComparingTo("-25.75");
        assertThat(rows.get(0).isOverBudget()).isTrue();
        assertThat(rows.get(0).isUnbudgeted()).isFalse();
    }

    @Test
    void reportsUnbudgetedCategoryAndZeroSpendingForEmptyMonth() {
        Category reportCategory = reportCategory();
        when(budgetRepository.findAllByMonthOrderByCategory_NameAsc(LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of());
        when(categoryRepository.findAllByTypeOrderByNameAsc(TransactionType.EXPENSE))
                .thenReturn(List.of(reportCategory));
        when(transactionRepository.sumExpensesByCategoryAndDateRange(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)
        )).thenReturn(List.of());

        List<BudgetReportRow> rows = budgetService.getReport(YearMonth.of(2026, 10));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).amountSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(rows.get(0).budgetAmount()).isNull();
        assertThat(rows.get(0).remainingAmount()).isNull();
        assertThat(rows.get(0).isUnbudgeted()).isTrue();
        assertThat(rows.get(0).isOverBudget()).isFalse();
    }

    @Test
    void keepsEntireBudgetRemainingWhenThereIsNoSpending() {
        Category reportCategory = reportCategory();
        MonthlyBudget budget = new MonthlyBudget(
                LocalDate.of(2026, 10, 1),
                new BigDecimal("300.00"),
                reportCategory
        );
        when(budgetRepository.findAllByMonthOrderByCategory_NameAsc(LocalDate.of(2026, 10, 1)))
                .thenReturn(List.of(budget));
        when(categoryRepository.findAllByTypeOrderByNameAsc(TransactionType.EXPENSE))
                .thenReturn(List.of(reportCategory));
        when(transactionRepository.sumExpensesByCategoryAndDateRange(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)
        )).thenReturn(List.of());

        List<BudgetReportRow> rows = budgetService.getReport(YearMonth.of(2026, 10));

        assertThat(rows.get(0).amountSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(rows.get(0).remainingAmount()).isEqualTo(new BigDecimal("300.00"));
    }

    @Test
    void usesJanuaryAsExclusiveEndForDecemberBudgetReport() {
        when(budgetRepository.findAllByMonthOrderByCategory_NameAsc(LocalDate.of(2026, 12, 1)))
                .thenReturn(List.of());
        when(categoryRepository.findAllByTypeOrderByNameAsc(TransactionType.EXPENSE))
                .thenReturn(List.of());
        when(transactionRepository.sumExpensesByCategoryAndDateRange(
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1)
        )).thenReturn(List.of());

        assertThat(budgetService.getReport(YearMonth.of(2026, 12))).isEmpty();
    }

    private Category reportCategory() {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(4L);
        when(category.getName()).thenReturn("Dining");
        return category;
    }
}

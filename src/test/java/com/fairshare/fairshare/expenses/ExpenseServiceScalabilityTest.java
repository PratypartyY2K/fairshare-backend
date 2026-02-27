package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.expenses.api.ExpenseResponse;
import com.fairshare.fairshare.expenses.model.Expense;
import com.fairshare.fairshare.expenses.model.ExpenseParticipant;
import com.fairshare.fairshare.expenses.service.ExpenseService;
import com.fairshare.fairshare.groups.repository.GroupMemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceScalabilityTest {

    @Mock
    private ExpenseRepository expenseRepo;
    @Mock
    private ExpenseParticipantRepository participantRepo;
    @Mock
    private LedgerEntryRepository ledgerRepo;
    @Mock
    private GroupMemberRepository groupMemberRepo;
    @Mock
    private ConfirmedTransferRepository confirmedTransferRepo;
    @Mock
    private ExpenseEventRepository eventRepo;
    @Mock
    private EntityManager em;
    @Captor
    private ArgumentCaptor<List<Long>> idsCaptor;

    @Test
    void listExpenses_usesBatchParticipantLookupPerPage() {
        ExpenseService service = new ExpenseService(
                expenseRepo,
                participantRepo,
                ledgerRepo,
                groupMemberRepo,
                confirmedTransferRepo,
                eventRepo,
                em
        );

        Long groupId = 77L;
        Long actorId = 501L;
        when(groupMemberRepo.existsByGroupIdAndUserId(groupId, actorId)).thenReturn(true);

        Expense ex1 = new Expense(groupId, 1001L, "Dinner", new BigDecimal("30.00"));
        Expense ex2 = new Expense(groupId, 1002L, "Taxi", new BigDecimal("20.00"));
        ReflectionTestUtils.setField(ex1, "id", 1L);
        ReflectionTestUtils.setField(ex2, "id", 2L);

        org.mockito.Mockito.when(expenseRepo.findByGroupIdAndVoidedFalse(eq(groupId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ex1, ex2), PageRequest.of(0, 2), 2));

        ExpenseParticipant p1 = new ExpenseParticipant(ex1, 1001L, new BigDecimal("15.00"));
        ExpenseParticipant p2 = new ExpenseParticipant(ex1, 1002L, new BigDecimal("15.00"));
        ExpenseParticipant p3 = new ExpenseParticipant(ex2, 1001L, new BigDecimal("10.00"));
        ExpenseParticipant p4 = new ExpenseParticipant(ex2, 1002L, new BigDecimal("10.00"));
        when(participantRepo.findByExpenseIdInOrderByExpenseIdAscUserIdAsc(anyList()))
                .thenReturn(List.of(p1, p2, p3, p4));

        PaginatedResponse<ExpenseResponse> response = service.listExpenses(groupId, actorId, 0, 2, "createdAt,desc", null, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).splits()).hasSize(2);
        assertThat(response.items().get(1).splits()).hasSize(2);

        verify(participantRepo, times(1)).findByExpenseIdInOrderByExpenseIdAscUserIdAsc(anyList());
        verify(participantRepo, never()).findByExpense_Id(anyLong());

        verify(participantRepo).findByExpenseIdInOrderByExpenseIdAscUserIdAsc(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
    }
}

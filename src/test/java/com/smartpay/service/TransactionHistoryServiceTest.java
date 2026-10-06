package com.smartpay.service;

import com.smartpay.dto.PageResponse;
import com.smartpay.dto.TransactionHistoryItem;
import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;
import com.smartpay.entity.Wallet;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private TransactionHistoryService historyService;

    @Test
    void getHistory_noWallet_returnsEmptyPage() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        PageResponse<TransactionHistoryItem> result = historyService.getHistory(1L, 0, 20);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
    }

    @Test
    void getHistory_marksSentAndFindsCounterparty() {
        Wallet myWallet = mock(Wallet.class);
        when(myWallet.getId()).thenReturn(10L);
        Wallet otherWallet = mock(Wallet.class);
        when(otherWallet.getId()).thenReturn(20L);
        when(otherWallet.getUserId()).thenReturn(2L);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(myWallet));

        Transaction tx = new Transaction("ref-1", "1:key1", 10L, 20L, new BigDecimal("30.00"),
                TransactionType.TRANSFER, TransactionStatus.SUCCESS);
        when(transactionRepository.findByWalletId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<Transaction>(List.of(tx), PageRequest.of(0, 20), 1));
        when(walletRepository.findAllById(any())).thenReturn(List.of(otherWallet));

        PageResponse<TransactionHistoryItem> result = historyService.getHistory(1L, 0, 20);

        assertEquals(1, result.content().size());
        TransactionHistoryItem item = result.content().get(0);
        assertEquals("SENT", item.direction());
        assertEquals(Long.valueOf(2L), item.counterpartyUserId());
        assertEquals(1, result.totalElements());
    }

    @Test
    void getHistory_depositIsReceivedWithNoCounterparty() {
        Wallet myWallet = mock(Wallet.class);
        when(myWallet.getId()).thenReturn(10L);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(myWallet));

        Transaction deposit = new Transaction(
                "deposit-ref",
                "deposit-key",
                null,
                10L,
                new BigDecimal("500.00"),
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS
        );

        when(transactionRepository.findByWalletId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(deposit),
                        PageRequest.of(0, 20),
                        1
                ));
        when(walletRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<TransactionHistoryItem> result =
                historyService.getHistory(1L, 0, 20);

        assertEquals(1, result.content().size());

        TransactionHistoryItem item = result.content().get(0);

        assertEquals("RECEIVED", item.direction());
        assertEquals(TransactionType.DEPOSIT, item.type());
        assertEquals(TransactionStatus.SUCCESS, item.status());
        assertEquals(0, new BigDecimal("500.00").compareTo(item.amount()));
        assertEquals(null, item.counterpartyUserId());
        assertEquals(1, result.totalElements());
    }

    @Test
    void getHistory_capsPageSizeAndFixesNegativePage() {
        Wallet myWallet = mock(Wallet.class);
        when(myWallet.getId()).thenReturn(10L);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(myWallet));
        when(transactionRepository.findByWalletId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        historyService.getHistory(1L, -3, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findByWalletId(eq(10L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }
}

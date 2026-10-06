package com.smartpay.service;

import com.smartpay.dto.TransferRequest;
import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;
import com.smartpay.entity.Wallet;
import com.smartpay.exception.BusinessRuleException;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferProcessor transferProcessor;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private TransferService transferService;

    private Transaction existingTransaction() {
        return new Transaction("ref-1", "1:key1", 10L, 20L, new BigDecimal("30.00"),
                TransactionType.TRANSFER, TransactionStatus.SUCCESS);
    }

    private void stubWalletIds() {
        Wallet senderWallet = mock(Wallet.class);
        Wallet receiverWallet = mock(Wallet.class);
        when(senderWallet.getId()).thenReturn(10L);
        when(receiverWallet.getId()).thenReturn(20L);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(receiverWallet));
    }

    @Test
    void transfer_missingKey_returns400() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.transfer(1L, new TransferRequest(2L, new BigDecimal("5.00")), null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(transferProcessor);
    }

    @Test
    void transfer_toSelf_returns400() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.transfer(1L, new TransferRequest(1L, new BigDecimal("5.00")), "key1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(transferProcessor, transactionRepository, walletRepository);
    }

    @Test
    void transfer_newKey_processesTransfer() {
        Transaction created = existingTransaction();
        when(transactionRepository.findByIdempotencyKey("1:key1")).thenReturn(Optional.empty());
        when(transferProcessor.process(eq(1L), any(TransferRequest.class), eq("1:key1"))).thenReturn(created);

        TransferResult result = transferService.transfer(1L, new TransferRequest(2L, new BigDecimal("30.00")), "key1");

        assertFalse(result.replayed());
        assertEquals("ref-1", result.transaction().getReferenceId());
    }

    @Test
    void transfer_sameKeySameRequest_returnsOldResultWithoutMovingMoney() {
        when(transactionRepository.findByIdempotencyKey("1:key1")).thenReturn(Optional.of(existingTransaction()));
        stubWalletIds();

        TransferResult result = transferService.transfer(1L, new TransferRequest(2L, new BigDecimal("30.00")), "key1");

        assertTrue(result.replayed());
        assertEquals("ref-1", result.transaction().getReferenceId());
        verifyNoInteractions(transferProcessor);
    }

    @Test
    void transfer_sameKeyDifferentAmount_returns409() {
        when(transactionRepository.findByIdempotencyKey("1:key1")).thenReturn(Optional.of(existingTransaction()));
        stubWalletIds();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.transfer(1L, new TransferRequest(2L, new BigDecimal("31.00")), "key1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(transferProcessor);
    }

    @Test
    void transfer_duplicateKeyRace_returnsWinningTransaction() {
        when(transactionRepository.findByIdempotencyKey("1:key1"))
                .thenReturn(Optional.empty(), Optional.of(existingTransaction()));
        when(transferProcessor.process(eq(1L), any(TransferRequest.class), eq("1:key1")))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        stubWalletIds();

        TransferResult result = transferService.transfer(1L, new TransferRequest(2L, new BigDecimal("30.00")), "key1");

        assertTrue(result.replayed());
        assertEquals("ref-1", result.transaction().getReferenceId());
    }
}
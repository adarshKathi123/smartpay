package com.smartpay.service;

import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;
import com.smartpay.entity.User;
import com.smartpay.entity.UserStatus;
import com.smartpay.entity.Wallet;
import com.smartpay.exception.BusinessRuleException;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void getWallet_createsEmptyWallet_whenMissing() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet wallet = walletService.getWallet(1L);

        assertEquals(Long.valueOf(1L), wallet.getUserId());
        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void deposit_addsAmountToExistingBalance_andRecordsDepositTransaction() {
        Wallet wallet = new Wallet(1L);
        wallet.setBalance(new BigDecimal("100.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.deposit(1L, new BigDecimal("50.00"));

        assertEquals(0, result.getBalance().compareTo(new BigDecimal("150.00")));
        verify(walletRepository).save(wallet);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction transaction = captor.getValue();
        assertEquals(TransactionType.DEPOSIT, transaction.getType());
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(transaction.getAmount()));
        assertEquals(null, transaction.getSenderWalletId());
        assertEquals(wallet.getId(), transaction.getReceiverWalletId());
        assertNotNull(transaction.getReferenceId());
        assertNotNull(transaction.getIdempotencyKey());
    }

    @Test
    void deposit_createsWalletAndAddsAmount_whenMissing() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(mock(User.class)));
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet wallet = inv.getArgument(0);
            return wallet;
        });

        Wallet result = walletService.deposit(2L, new BigDecimal("25.50"));

        assertEquals(Long.valueOf(2L), result.getUserId());
        assertEquals(0, result.getBalance().compareTo(new BigDecimal("25.50")));

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void deposit_frozenUser_isRejected() {
        User frozen = mock(User.class);
        when(frozen.getStatus()).thenReturn(UserStatus.FROZEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(frozen));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.deposit(1L, new BigDecimal("10.00")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(walletRepository);
        verifyNoInteractions(transactionRepository);
    }
}
package com.smartpay.service;

import com.smartpay.dto.TransferRequest;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferProcessorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransferProcessor transferProcessor;

    private Wallet walletWithBalance(Long userId, String balance) {
        Wallet wallet = new Wallet(userId);
        wallet.setBalance(new BigDecimal(balance));
        return wallet;
    }

    private void stubWalletLocks(Wallet senderWallet, Wallet receiverWallet) {
        when(walletRepository.existsByUserId(1L)).thenReturn(true);
        when(walletRepository.existsByUserId(2L)).thenReturn(true);
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(receiverWallet));
    }

    @Test
    void process_movesMoneyAndRecordsTransaction() {
        Wallet senderWallet = walletWithBalance(1L, "100.00");
        Wallet receiverWallet = walletWithBalance(2L, "10.00");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mock(User.class)));
        stubWalletLocks(senderWallet, receiverWallet);
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = transferProcessor.process(1L, new TransferRequest(2L, new BigDecimal("30.00")), "1:key1");

        assertEquals(0, senderWallet.getBalance().compareTo(new BigDecimal("70.00")));
        assertEquals(0, receiverWallet.getBalance().compareTo(new BigDecimal("40.00")));
        assertEquals(TransactionType.TRANSFER, tx.getType());
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
        assertEquals("1:key1", tx.getIdempotencyKey());
        assertEquals(0, tx.getAmount().compareTo(new BigDecimal("30.00")));
    }

    @Test
    void process_insufficientBalance_isRejectedAndNothingChanges() {
        Wallet senderWallet = walletWithBalance(1L, "20.00");
        Wallet receiverWallet = walletWithBalance(2L, "10.00");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mock(User.class)));
        stubWalletLocks(senderWallet, receiverWallet);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferProcessor.process(1L, new TransferRequest(2L, new BigDecimal("30.00")), "1:key1"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals(0, senderWallet.getBalance().compareTo(new BigDecimal("20.00")));
        assertEquals(0, receiverWallet.getBalance().compareTo(new BigDecimal("10.00")));
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void process_frozenSender_isRejected() {
        User frozenSender = mock(User.class);
        when(frozenSender.getStatus()).thenReturn(UserStatus.FROZEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(frozenSender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mock(User.class)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferProcessor.process(1L, new TransferRequest(2L, new BigDecimal("5.00")), "1:key1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void process_frozenReceiver_isRejected() {
        User frozenReceiver = mock(User.class);
        when(frozenReceiver.getStatus()).thenReturn(UserStatus.FROZEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(frozenReceiver));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferProcessor.process(1L, new TransferRequest(2L, new BigDecimal("5.00")), "1:key1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void process_receiverNotFound_returns404() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferProcessor.process(1L, new TransferRequest(2L, new BigDecimal("5.00")), "1:key1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
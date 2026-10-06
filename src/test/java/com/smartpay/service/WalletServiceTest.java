package com.smartpay.service;

import com.smartpay.entity.Wallet;
import com.smartpay.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

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
    void deposit_addsAmountToExistingBalance() {
        Wallet wallet = new Wallet(1L);
        wallet.setBalance(new BigDecimal("100.00"));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.deposit(1L, new BigDecimal("50.00"));

        assertEquals(0, result.getBalance().compareTo(new BigDecimal("150.00")));
        verify(walletRepository).save(wallet);
    }

    @Test
    void deposit_createsWalletAndAddsAmount_whenMissing() {
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.deposit(2L, new BigDecimal("25.50"));

        assertEquals(Long.valueOf(2L), result.getUserId());
        assertEquals(0, result.getBalance().compareTo(new BigDecimal("25.50")));
    }
}
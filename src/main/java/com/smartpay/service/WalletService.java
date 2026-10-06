package com.smartpay.service;

import com.smartpay.entity.Wallet;
import com.smartpay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    // Returns the user's wallet. Creates an empty one on first access.
    @Transactional
    public Wallet getWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId)));
    }

    // Adds money to the wallet. The row is locked while we update it.
    @Transactional
    public Wallet deposit(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> new Wallet(userId));
        wallet.setBalance(wallet.getBalance().add(amount));
        return walletRepository.save(wallet);
    }
}
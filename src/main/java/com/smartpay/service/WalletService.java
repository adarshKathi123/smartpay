package com.smartpay.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;
import com.smartpay.entity.User;
import com.smartpay.entity.Wallet;
import com.smartpay.exception.BusinessRuleException;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(WalletRepository walletRepository,
                         UserRepository userRepository,
                         TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Wallet getWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId)));
    }

    @Transactional
    public Wallet deposit(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        if (user.getStatus() != null && "FROZEN".equals(user.getStatus().name())) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "Your account is frozen");
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> new Wallet(userId));

        wallet.setBalance(wallet.getBalance().add(amount));

        Wallet savedWallet = walletRepository.save(wallet);

        Transaction depositTransaction = new Transaction(
                UUID.randomUUID().toString(),
                "deposit-" + UUID.randomUUID(),
                null,
                savedWallet.getId(),
                amount,
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS
        );

        transactionRepository.save(depositTransaction);

        return savedWallet;
    }
}
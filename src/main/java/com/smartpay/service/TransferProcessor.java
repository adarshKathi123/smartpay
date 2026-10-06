package com.smartpay.service;

import com.smartpay.dto.TransferRequest;
import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;
import com.smartpay.entity.User;
import com.smartpay.entity.Wallet;
import com.smartpay.exception.BusinessRuleException;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

// Moves the money. Everything inside process() is ONE database transaction:
// either both wallets change and the record is saved, or nothing changes.
@Service
public class TransferProcessor {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransferProcessor(UserRepository userRepository,
                             WalletRepository walletRepository,
                             TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction process(Long senderUserId, TransferRequest request, String storedKey) {
        Long receiverUserId = request.receiverUserId();
        BigDecimal amount = request.amount();

        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new IllegalStateException("Sender not found: " + senderUserId));
        User receiver = userRepository.findById(receiverUserId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Receiver not found"));

        if (isFrozen(sender)) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "Your account is frozen");
        }
        if (isFrozen(receiver)) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "Receiver account is frozen");
        }

        // Make sure both wallets exist (checks only, loads nothing)
        ensureWalletExists(senderUserId);
        ensureWalletExists(receiverUserId);

        // Always lock the wallet of the smaller user id first.
        // Two opposite transfers (A to B and B to A) then lock in the same order, so they cannot deadlock.
        Wallet senderWallet;
        Wallet receiverWallet;
        if (senderUserId < receiverUserId) {
            senderWallet = lockWallet(senderUserId);
            receiverWallet = lockWallet(receiverUserId);
        } else {
            receiverWallet = lockWallet(receiverUserId);
            senderWallet = lockWallet(senderUserId);
        }

        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        Transaction transaction = new Transaction(
                UUID.randomUUID().toString(),
                storedKey,
                senderWallet.getId(),
                receiverWallet.getId(),
                amount,
                TransactionType.TRANSFER,
                TransactionStatus.SUCCESS
        );
        // saveAndFlush sends the SQL now, so a duplicate Idempotency-Key fails here
        return transactionRepository.saveAndFlush(transaction);
    }

    private void ensureWalletExists(Long userId) {
        if (!walletRepository.existsByUserId(userId)) {
            walletRepository.save(new Wallet(userId));
        }
    }

    private Wallet lockWallet(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user " + userId));
    }

    private boolean isFrozen(User user) {
        return user.getStatus() != null && "FROZEN".equals(user.getStatus().name());
    }
}
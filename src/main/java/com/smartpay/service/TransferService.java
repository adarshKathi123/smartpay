package com.smartpay.service;

import com.smartpay.dto.TransferRequest;
import com.smartpay.entity.Transaction;
import com.smartpay.entity.Wallet;
import com.smartpay.exception.BusinessRuleException;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Handles the Idempotency-Key. This class is deliberately NOT @Transactional:
// the money movement runs in TransferProcessor, and if it fails because of a
// duplicate key we can still read the winning transaction here.
@Service
public class TransferService {

    private static final int MAX_KEY_LENGTH = 100;

    private final TransferProcessor transferProcessor;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransferService(TransferProcessor transferProcessor,
                           TransactionRepository transactionRepository,
                           WalletRepository walletRepository) {
        this.transferProcessor = transferProcessor;
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    public TransferResult transfer(Long senderUserId, TransferRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required (max 100 characters)");
        }
        if (senderUserId.equals(request.receiverUserId())) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "You cannot transfer money to yourself");
        }

        // The key is stored with the sender id in front, so two users can never collide
        String storedKey = senderUserId + ":" + idempotencyKey.trim();

        Optional<Transaction> existing = findReplay(senderUserId, request, storedKey);
        if (existing.isPresent()) {
            return new TransferResult(existing.get(), true);
        }

        try {
            Transaction created = transferProcessor.process(senderUserId, request, storedKey);
            return new TransferResult(created, false);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same key ran at the same time and the other one won.
            // Our transaction was rolled back (no money moved), so return the winner.
            Optional<Transaction> winner = findReplay(senderUserId, request, storedKey);
            if (winner.isPresent()) {
                return new TransferResult(winner.get(), true);
            }
            throw e;
        }
    }

    // Returns the old transaction if this key was used before for the SAME request.
    // Throws 409 if the key was used for a different request.
    private Optional<Transaction> findReplay(Long senderUserId, TransferRequest request, String storedKey) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(storedKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Transaction old = existing.get();

        Long senderWalletId = walletRepository.findByUserId(senderUserId).map(Wallet::getId).orElse(null);
        Long receiverWalletId = walletRepository.findByUserId(request.receiverUserId()).map(Wallet::getId).orElse(null);

        boolean sameRequest = senderWalletId != null && senderWalletId.equals(old.getSenderWalletId())
                && receiverWalletId != null && receiverWalletId.equals(old.getReceiverWalletId())
                && old.getAmount().compareTo(request.amount()) == 0;

        if (!sameRequest) {
            throw new BusinessRuleException(HttpStatus.CONFLICT,
                    "This Idempotency-Key was already used for a different request");
        }
        return Optional.of(old);
    }
}
package com.smartpay.service;

import com.smartpay.dto.PageResponse;
import com.smartpay.dto.TransactionHistoryItem;
import com.smartpay.entity.Transaction;
import com.smartpay.entity.Wallet;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class TransactionHistoryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransactionHistoryService(TransactionRepository transactionRepository,
                                     WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionHistoryItem> getHistory(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Optional<Wallet> wallet = walletRepository.findByUserId(userId);
        if (wallet.isEmpty()) {
            return new PageResponse<>(List.of(), safePage, safeSize, 0, 0);
        }
        Long walletId = wallet.get().getId();

        // Newest first. The id is a tie-breaker so pages never repeat or skip rows.
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        Page<Transaction> result = transactionRepository.findByWalletId(walletId, pageable);

        // Find the user id of the other side with ONE query for the whole page
        Set<Long> otherWalletIds = new HashSet<>();
        for (Transaction t : result.getContent()) {
            Long other = walletId.equals(t.getSenderWalletId()) ? t.getReceiverWalletId() : t.getSenderWalletId();
            if (other != null) {
                otherWalletIds.add(other);
            }
        }
        Map<Long, Long> walletIdToUserId = new HashMap<>();
        for (Wallet w : walletRepository.findAllById(otherWalletIds)) {
            walletIdToUserId.put(w.getId(), w.getUserId());
        }

        List<TransactionHistoryItem> items = new ArrayList<>();
        for (Transaction t : result.getContent()) {
            boolean sent = walletId.equals(t.getSenderWalletId());
            Long otherWalletId = sent ? t.getReceiverWalletId() : t.getSenderWalletId();
            Long otherUserId = otherWalletId == null ? null : walletIdToUserId.get(otherWalletId);
            items.add(new TransactionHistoryItem(
                    t.getReferenceId(),
                    sent ? "SENT" : "RECEIVED",
                    t.getType(),
                    t.getStatus(),
                    t.getAmount(),
                    otherUserId,
                    t.getCreatedAt()
            ));
        }

        return new PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
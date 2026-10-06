package com.smartpay.repository;

import com.smartpay.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    // All transactions where this wallet is the sender or the receiver
    @Query("select t from Transaction t where t.senderWalletId = :walletId or t.receiverWalletId = :walletId")
    Page<Transaction> findByWalletId(@Param("walletId") Long walletId, Pageable pageable);
}
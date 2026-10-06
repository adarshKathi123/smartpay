package com.smartpay.integration;

import com.smartpay.entity.Role;
import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
import com.smartpay.entity.TransactionType;
import com.smartpay.entity.User;
import com.smartpay.entity.UserStatus;
import com.smartpay.entity.Wallet;
import com.smartpay.repository.TransactionRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.repository.WalletRepository;
import com.smartpay.service.TransactionHistoryService;
import com.smartpay.service.WalletService;
import com.smartpay.dto.PageResponse;
import com.smartpay.dto.TransactionHistoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DepositIntegrationTest {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionHistoryService transactionHistoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanTestDatabase() {
        assertTrue(datasourceUrl.contains("smartpay_test_db"),
                "Refusing to run: these tests must use smartpay_test_db, but the URL is "
                        + datasourceUrl);

        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void successfulDeposit_updatesWallet_andCreatesDepositTransaction() {
        User user = createUser("DepositUser");

        Wallet result = walletService.deposit(
                user.getId(),
                new BigDecimal("500.00")
        );

        assertEquals(0, new BigDecimal("500.00").compareTo(result.getBalance()));

        Wallet savedWallet = walletRepository.findByUserId(user.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("500.00").compareTo(savedWallet.getBalance()));

        assertEquals(1, transactionRepository.count());

        Transaction transaction = transactionRepository.findAll().get(0);

        assertEquals(TransactionType.DEPOSIT, transaction.getType());
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(0, new BigDecimal("500.00").compareTo(transaction.getAmount()));
        assertNull(transaction.getSenderWalletId());
        assertEquals(savedWallet.getId(), transaction.getReceiverWalletId());
        assertTrue(transaction.getReferenceId() != null && !transaction.getReferenceId().isBlank());
        assertTrue(transaction.getIdempotencyKey() != null && !transaction.getIdempotencyKey().isBlank());
    }

    @Test
    void successfulDeposit_appearsInTransactionHistory() {
        User user = createUser("HistoryDepositUser");

        walletService.deposit(
                user.getId(),
                new BigDecimal("250.00")
        );

        PageResponse<TransactionHistoryItem> history =
                transactionHistoryService.getHistory(user.getId(), 0, 20);

        assertEquals(1, history.content().size());

        TransactionHistoryItem item = history.content().get(0);

        assertEquals("RECEIVED", item.direction());
        assertEquals(TransactionType.DEPOSIT, item.type());
        assertEquals(TransactionStatus.SUCCESS, item.status());
        assertEquals(0, new BigDecimal("250.00").compareTo(item.amount()));
        assertNull(item.counterpartyUserId());
        assertEquals(1, history.totalElements());
    }

    private User createUser(String name) {
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        user.setPasswordHash("not-a-real-hash");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userRepository.save(user);
    }
}

package com.smartpay.integration;

import com.smartpay.dto.TransferRequest;
import com.smartpay.entity.Role;
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
import com.smartpay.service.TransferResult;
import com.smartpay.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Integration test: real Spring context, real services, real JPA/Hibernate, real MySQL.
// It runs against smartpay_test_db (see src/test/resources/application-test.properties).
// The class is deliberately NOT @Transactional: we want each transfer to really commit
// or roll back, exactly like it does when the application runs.
@SpringBootTest
@ActiveProfiles("test")
class TransferIntegrationTest {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanTestDatabase() {
        // Safety check: never delete data unless we are on the test database
        assertTrue(datasourceUrl.contains("smartpay_test_db"),
                "Refusing to run: these tests must use smartpay_test_db, but the URL is " + datasourceUrl);

        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void successfulTransfer_movesMoneyAndSavesTransaction() {
        User sender = createUser("Sender");
        User receiver = createUser("Receiver");
        Wallet senderWallet = createWallet(sender, "100.00");
        Wallet receiverWallet = createWallet(receiver, "20.00");

        TransferResult result = transferService.transfer(
                sender.getId(), transferRequest(receiver, "30.00"), "key-success");

        assertFalse(result.replayed());
        assertBalance(sender, "70.00");
        assertBalance(receiver, "50.00");

        assertEquals(1, transactionRepository.count());
        Transaction saved = transactionRepository.findAll().get(0);
        assertEquals(TransactionType.TRANSFER, saved.getType());
        assertEquals(TransactionStatus.SUCCESS, saved.getStatus());
        assertEquals(0, new BigDecimal("30.00").compareTo(saved.getAmount()));
        assertEquals(senderWallet.getId(), saved.getSenderWalletId());
        assertEquals(receiverWallet.getId(), saved.getReceiverWalletId());
    }

    @Test
    void insufficientBalance_failsAndLeavesDatabaseUnchanged() {
        User sender = createUser("Sender");
        User receiver = createUser("Receiver");
        createWallet(sender, "20.00");
        createWallet(receiver, "10.00");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.transfer(
                        sender.getId(), transferRequest(receiver, "30.00"), "key-insufficient"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("Insufficient balance", ex.getMessage());

        assertBalance(sender, "20.00");
        assertBalance(receiver, "10.00");
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void sameIdempotencyKeyTwice_movesMoneyOnlyOnce() {
        User sender = createUser("Sender");
        User receiver = createUser("Receiver");
        createWallet(sender, "100.00");
        createWallet(receiver, "20.00");
        TransferRequest request = transferRequest(receiver, "30.00");

        TransferResult first = transferService.transfer(sender.getId(), request, "key-same");
        TransferResult second = transferService.transfer(sender.getId(), request, "key-same");

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.transaction().getReferenceId(), second.transaction().getReferenceId());

        assertBalance(sender, "70.00");
        assertBalance(receiver, "50.00");
        assertEquals(1, transactionRepository.count());
    }

    // ---------- helpers: every test creates its own users and wallets ----------

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

    private Wallet createWallet(User user, String balance) {
        Wallet wallet = new Wallet(user.getId());
        wallet.setBalance(new BigDecimal(balance));
        return walletRepository.save(wallet);
    }

    private TransferRequest transferRequest(User receiver, String amount) {
        return new TransferRequest(receiver.getId(), new BigDecimal(amount));
    }

    private void assertBalance(User user, String expected) {
        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal(expected).compareTo(wallet.getBalance()),
                "Unexpected balance for " + user.getName() + ": " + wallet.getBalance());
    }
}

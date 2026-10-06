package com.smartpay.integration;

import com.smartpay.dto.TransferRequest;
import com.smartpay.entity.Role;
import com.smartpay.entity.Transaction;
import com.smartpay.entity.TransactionStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Concurrency test against the real MySQL test database.
// Several threads call the real TransferService at the same moment. This checks that the
// existing pessimistic wallet locking and database transaction keep the money correct.
// It shows concurrency safety for this portfolio project; it is not a load or scalability test.
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransferIntegrationTest {

    private static final int THREADS = 5;

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

    // Balance 100, five requests of 30 at the same time.
    // Only three can succeed (3 x 30 = 90). The other two must fail with "Insufficient balance".
    // Without locking, several requests could read 100 at once and the wallet could go negative.
    @Test
    void concurrentTransfersFromOneWallet_neverOverspend() throws Exception {
        User sender = createUser("Sender");
        User receiver = createUser("Receiver");
        createWallet(sender, "100.00");
        createWallet(receiver, "0.00");

        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            calls.add(new Call(sender.getId(), transferRequest(receiver, "30.00"), "key-same-wallet-" + i));
        }

        Outcome outcome = runConcurrently(calls);

        assertTrue(outcome.unexpected.isEmpty(), "Unexpected errors: " + outcome.unexpected);
        assertEquals(3, outcome.succeeded);
        assertEquals(2, outcome.insufficientBalance);

        assertBalance(sender, "10.00");
        assertBalance(receiver, "90.00");
        assertTotalMoney(sender, receiver, "100.00");

        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(3, transactions.size());
        assertTrue(transactions.stream().allMatch(t -> t.getStatus() == TransactionStatus.SUCCESS));
        BigDecimal transferred = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("90.00").compareTo(transferred));
    }

    // A sends to B while B sends to A at the same time. Both wallets are locked in the same
    // order (lower user id first), so the two directions cannot deadlock.
    @Test
    void oppositeTransfersAtTheSameTime_completeWithoutDeadlock() throws Exception {
        User userA = createUser("UserA");
        User userB = createUser("UserB");
        createWallet(userA, "100.00");
        createWallet(userB, "100.00");

        List<Call> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                calls.add(new Call(userA.getId(), transferRequest(userB, "10.00"), "key-opposite-" + i));
            } else {
                calls.add(new Call(userB.getId(), transferRequest(userA, "10.00"), "key-opposite-" + i));
            }
        }

        Outcome outcome = runConcurrently(calls);

        assertTrue(outcome.unexpected.isEmpty(), "Unexpected errors: " + outcome.unexpected);
        assertEquals(10, outcome.succeeded);

        // 5 x 10 each way cancels out
        assertBalance(userA, "100.00");
        assertBalance(userB, "100.00");
        assertTotalMoney(userA, userB, "200.00");
        assertEquals(10, transactionRepository.count());
    }

    // ---------- running requests at the same time ----------

    private record Call(Long senderId, TransferRequest request, String key) {
    }

    private static class Outcome {
        int succeeded;
        int insufficientBalance;
        final List<Throwable> unexpected = new ArrayList<>();
    }

    // All tasks wait at a "starting gun" (the latch) and are released together.
    private Outcome runConcurrently(List<Call> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<TransferResult>> futures = new ArrayList<>();
        try {
            for (Call call : calls) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    return transferService.transfer(call.senderId(), call.request(), call.key());
                }));
            }
            startSignal.countDown();

            Outcome outcome = new Outcome();
            for (Future<TransferResult> future : futures) {
                try {
                    // A timeout here would mean the requests are stuck (for example a deadlock)
                    future.get(30, TimeUnit.SECONDS);
                    outcome.succeeded++;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof BusinessRuleException rule
                            && rule.getStatus() == HttpStatus.UNPROCESSABLE_ENTITY) {
                        outcome.insufficientBalance++;
                    } else {
                        outcome.unexpected.add(cause);
                    }
                }
            }
            return outcome;
        } finally {
            pool.shutdownNow();
        }
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

    private BigDecimal balanceOf(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance();
    }

    private void assertBalance(User user, String expected) {
        BigDecimal actual = balanceOf(user);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "Unexpected balance for " + user.getName() + ": " + actual);
    }

    // Money is only moved, never created or destroyed
    private void assertTotalMoney(User first, User second, String expectedTotal) {
        BigDecimal total = balanceOf(first).add(balanceOf(second));
        assertEquals(0, new BigDecimal(expectedTotal).compareTo(total), "Total money changed: " + total);
    }
}

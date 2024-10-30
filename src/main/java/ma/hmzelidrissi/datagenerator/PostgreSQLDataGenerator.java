package ma.hmzelidrissi.datagenerator;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hmzelidrissi.datagenerator.enums.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.sql.Date;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostgreSQLDataGenerator implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Faker faker = new Faker();
    private final Random random = new Random();

    private static final int TOTAL_USERS = 3_000_000;
    private static final int ACCOUNTS_PER_USER = 2;
    private static final int TRANSACTIONS_PER_ACCOUNT = 6;
    private static final int INVOICES_PER_USER = 2;
    private static final int LOANS_PER_USER = 2;
    private static final int BATCH_SIZE = 1000;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting PostgreSQL direct data generation...");
        long startTime = System.currentTimeMillis();

        try {
            createTables();
            List<Long> userIds = generateUsers();
            Map<Long, List<Long>> userAccounts = generateAccounts(userIds);
            generateTransactions(userAccounts);
            generateInvoices(userIds);
            generateLoans(userIds);
            createIndexes();

            long endTime = System.currentTimeMillis();
            log.info("Data generation completed in {} seconds", (endTime - startTime) / 1000);
        } catch (Exception e) {
            log.error("Error during data generation", e);
            throw new RuntimeException("Failed to generate data", e);
        }
    }

    private void createTables() {
        log.info("Creating tables...");
        jdbcTemplate.execute("""
            DROP TABLE IF EXISTS transactions CASCADE;
            DROP TABLE IF EXISTS loans CASCADE;
            DROP TABLE IF EXISTS invoices CASCADE;
            DROP TABLE IF EXISTS accounts CASCADE;
            DROP TABLE IF EXISTS users CASCADE;

            CREATE TABLE users (
                id BIGSERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE,
                password VARCHAR(255) NOT NULL,
                age INTEGER NOT NULL,
                monthly_income NUMERIC(15,2) NOT NULL,
                credit_score INTEGER NOT NULL,
                role VARCHAR(20) NOT NULL
            );

            CREATE TABLE accounts (
                id BIGSERIAL PRIMARY KEY,
                balance NUMERIC(15,2) NOT NULL,
                status VARCHAR(20) NOT NULL,
                user_id BIGINT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE transactions (
                id BIGSERIAL PRIMARY KEY,
                type VARCHAR(20) NOT NULL,
                amount NUMERIC(15,2) NOT NULL,
                source_account_id BIGINT NOT NULL,
                destination_account_id BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL,
                FOREIGN KEY (source_account_id) REFERENCES accounts(id),
                FOREIGN KEY (destination_account_id) REFERENCES accounts(id)
            );

            CREATE TABLE invoices (
                id BIGSERIAL PRIMARY KEY,
                amount_due NUMERIC(15,2) NOT NULL,
                due_date DATE NOT NULL,
                user_id BIGINT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE loans (
                id BIGSERIAL PRIMARY KEY,
                principal NUMERIC(15,2) NOT NULL,
                interest_rate NUMERIC(5,2) NOT NULL,
                term_months INTEGER NOT NULL,
                user_id BIGINT NOT NULL,
                approved BOOLEAN NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
        """);
    }

    private List<Long> generateUsers() {
        log.info("Generating {} users...", TOTAL_USERS);
        List<Long> userIds = new ArrayList<>();
        List<Object[]> batchArgs = new ArrayList<>();
        String hashedPassword = "$2a$10$0Pp2F39K/SwPJ9tmSNzk8.FWukmZTdGE/BiS4tXJ5QNSXGQTWdHY2";

        for (long i = 1; i <= TOTAL_USERS; i++) {
            userIds.add(i);
            Object[] userData = new Object[]{
                    faker.name().fullName(),
                    String.format("user%d@example.com", i),
                    hashedPassword,
                    random.nextInt(18, 80),
                    random.nextDouble() * 150000 + 30000,
                    random.nextInt(300, 850),
                    Role.values()[random.nextInt(Role.values().length)].toString()
            };
            batchArgs.add(userData);

            if (batchArgs.size() >= BATCH_SIZE) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO users (name, email, password, age, monthly_income, credit_score, role) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        batchArgs
                );
                batchArgs.clear();
                log.info("Generated {} users", i);
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO users (name, email, password, age, monthly_income, credit_score, role) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    batchArgs
            );
        }

        return userIds;
    }

    private Map<Long, List<Long>> generateAccounts(List<Long> userIds) {
        log.info("Generating accounts for {} users...", userIds.size());
        Map<Long, List<Long>> userAccounts = new HashMap<>();
        List<Object[]> batchArgs = new ArrayList<>();
        int count = 0;
        long accountId = 1;

        for (Long userId : userIds) {
            List<Long> accounts = new ArrayList<>();
            for (int i = 0; i < ACCOUNTS_PER_USER; i++) {
                accounts.add(accountId);

                Object[] accountData = new Object[]{
                        random.nextDouble() * 50000 + 1000,
                        AccountStatus.values()[random.nextInt(AccountStatus.values().length)].toString(),
                        userId
                };
                batchArgs.add(accountData);

                if (++count >= BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(
                            "INSERT INTO accounts (balance, status, user_id) VALUES (?, ?, ?)",
                            batchArgs
                    );
                    batchArgs.clear();
                    count = 0;
                    log.info("Generated {} accounts", accountId);
                }

                accountId++;
            }
            userAccounts.put(userId, accounts);
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO accounts (balance, status, user_id) VALUES (?, ?, ?)",
                    batchArgs
            );
        }

        return userAccounts;
    }

    private void generateTransactions(Map<Long, List<Long>> userAccounts) {
        log.info("Generating transactions...");
        List<Object[]> batchArgs = new ArrayList<>();
        int count = 0;
        long transactionId = 1;
        List<Long> allAccountIds = userAccounts.values().stream().flatMap(List::stream).toList();

        for (List<Long> accounts : userAccounts.values()) {
            for (Long sourceAccountId : accounts) {
                for (int i = 0; i < TRANSACTIONS_PER_ACCOUNT; i++) {
                    Long destinationAccountId = allAccountIds.get(random.nextInt(allAccountIds.size()));
                    while (destinationAccountId.equals(sourceAccountId)) {
                        destinationAccountId = allAccountIds.get(random.nextInt(allAccountIds.size()));
                    }

                    Object[] transactionData = new Object[]{
                            TransactionType.values()[random.nextInt(TransactionType.values().length)].toString(),
                            random.nextDouble() * 1000 + 10,
                            sourceAccountId,
                            destinationAccountId,
                            TransactionStatus.values()[random.nextInt(TransactionStatus.values().length)].toString()
                    };
                    batchArgs.add(transactionData);

                    if (++count >= BATCH_SIZE) {
                        jdbcTemplate.batchUpdate(
                                "INSERT INTO transactions (type, amount, source_account_id, destination_account_id, status) " +
                                        "VALUES (?, ?, ?, ?, ?)",
                                batchArgs
                        );
                        batchArgs.clear();
                        count = 0;
                        log.info("Generated {} transactions", transactionId);
                    }
                    transactionId++;
                }
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO transactions (type, amount, source_account_id, destination_account_id, status) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    batchArgs
            );
        }
    }

    private void generateInvoices(List<Long> userIds) {
        log.info("Generating invoices...");
        List<Object[]> batchArgs = new ArrayList<>();
        int count = 0;
        long invoiceId = 1;

        for (Long userId : userIds) {
            for (int i = 0; i < INVOICES_PER_USER; i++) {
                Object[] invoiceData = new Object[]{
                        random.nextDouble() * 5000 + 100,
                        Date.valueOf(LocalDate.now().plusDays(random.nextInt(365))),
                        userId
                };
                batchArgs.add(invoiceData);

                if (++count >= BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(
                            "INSERT INTO invoices (amount_due, due_date, user_id) VALUES (?, ?, ?)",
                            batchArgs
                    );
                    batchArgs.clear();
                    count = 0;
                    log.info("Generated {} invoices", invoiceId);
                }
                invoiceId++;
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO invoices (amount_due, due_date, user_id) VALUES (?, ?, ?)",
                    batchArgs
            );
        }
    }

    private void generateLoans(List<Long> userIds) {
        log.info("Generating loans...");
        List<Object[]> batchArgs = new ArrayList<>();
        int count = 0;
        long loanId = 1;

        for (Long userId : userIds) {
            for (int i = 0; i < LOANS_PER_USER; i++) {
                Object[] loanData = new Object[]{
                        random.nextDouble() * 500000 + 10000,
                        random.nextDouble() * 15 + 5,
                        random.nextInt(12, 360),
                        userId,
                        random.nextBoolean()
                };
                batchArgs.add(loanData);

                if (++count >= BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(
                            "INSERT INTO loans (principal, interest_rate, term_months, user_id, approved) " +
                                    "VALUES (?, ?, ?, ?, ?)",
                            batchArgs
                    );
                    batchArgs.clear();
                    count = 0;
                    log.info("Generated {} loans", loanId);
                }
                loanId++;
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO loans (principal, interest_rate, term_months, user_id, approved) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    batchArgs
            );
        }
    }

    private void createIndexes() {
        log.info("Creating indexes...");
        jdbcTemplate.execute("""
            CREATE INDEX idx_user_role ON users(role);
            CREATE INDEX idx_account_status ON accounts(status);
            CREATE INDEX idx_account_user ON accounts(user_id);
            CREATE INDEX idx_transaction_status ON transactions(status);
            CREATE INDEX idx_transaction_source ON transactions(source_account_id);
            CREATE INDEX idx_transaction_dest ON transactions(destination_account_id);
            CREATE INDEX idx_invoice_due_date ON invoices(due_date);
            CREATE INDEX idx_invoice_user ON invoices(user_id);
            CREATE INDEX idx_loan_user ON loans(user_id);
            CREATE INDEX idx_loan_approved ON loans(approved);
        """);
    }
}
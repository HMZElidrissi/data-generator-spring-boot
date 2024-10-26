package ma.hmzelidrissi.datagenerator;

import ma.hmzelidrissi.datagenerator.enums.*;
import com.github.javafaker.Faker;
import org.mindrot.jbcrypt.BCrypt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@SpringBootApplication
@Slf4j
public class DataGeneratorApplication implements CommandLineRunner {

  private final Faker faker = new Faker();
  private final Random random = new Random();

  private static final int TOTAL_USERS = 1000000; // 1 million users
  private static final int ACCOUNTS_PER_USER = 2; // 2 million accounts
  private static final int TRANSACTIONS_PER_ACCOUNT = 6; // 12 million transactions
  private static final int INVOICES_PER_USER = 1; // 1 million invoices
  private static final int LOANS_PER_USER = 1; // 1 million loans

  @Value("${generator.output.file}")
  private String OUTPUT_FILE;

  private static final int BATCH_SIZE = 1000;
  private static final String NEW_LINE = System.getProperty("line.separator");

  public static void main(String[] args) {
    SpringApplication.run(DataGeneratorApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    log.info("Starting data generation...");
    long startTime = System.currentTimeMillis();

    generateSQLFile();

    long endTime = System.currentTimeMillis();
    log.info("Data generation completed in {} seconds", (endTime - startTime) / 1000);
    log.info("SQL file generated: {}", OUTPUT_FILE);
  }

  private void generateSQLFile() {
    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(OUTPUT_FILE))) {
      writeSchemaCreation(writer);
      generateData(writer);
    } catch (IOException e) {
      log.error("Error generating SQL file", e);
      throw new RuntimeException("Failed to generate SQL file", e);
    }
  }

  private void writeSchemaCreation(BufferedWriter writer) throws IOException {
    log.info("Writing schema creation statements...");
    writer.write(
        """
            -- Drop existing tables
            DROP TABLE IF EXISTS transactions;
            DROP TABLE IF EXISTS loans;
            DROP TABLE IF EXISTS invoices;
            DROP TABLE IF EXISTS accounts;
            DROP TABLE IF EXISTS users;

            -- Create tables with proper relations
            CREATE TABLE users (
                id BIGINT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE,
                password VARCHAR(255) NOT NULL,
                age INT NOT NULL,
                monthly_income DOUBLE NOT NULL,
                credit_score INT NOT NULL,
                role VARCHAR(20) NOT NULL
            );

            CREATE TABLE accounts (
                id BIGINT PRIMARY KEY,
                balance DOUBLE NOT NULL,
                status VARCHAR(20) NOT NULL,
                user_id BIGINT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE transactions (
                id BIGINT PRIMARY KEY,
                type VARCHAR(20) NOT NULL,
                amount DOUBLE NOT NULL,
                source_account_id BIGINT NOT NULL,
                destination_account_id BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL,
                FOREIGN KEY (source_account_id) REFERENCES accounts(id),
                FOREIGN KEY (destination_account_id) REFERENCES accounts(id)
            );

            CREATE TABLE invoices (
                id BIGINT PRIMARY KEY,
                amount_due DOUBLE NOT NULL,
                due_date DATE NOT NULL,
                user_id BIGINT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            CREATE TABLE loans (
                id BIGINT PRIMARY KEY,
                principal DOUBLE NOT NULL,
                interest_rate DOUBLE NOT NULL,
                term_months INT NOT NULL,
                user_id BIGINT NOT NULL,
                approved BOOLEAN NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );

            -- Create indexes for better performance
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

  private void generateData(BufferedWriter writer) throws IOException {
    List<Long> userIds = generateUsers(writer);
    Map<Long, List<Long>> userAccounts = generateAccounts(writer, userIds);
    generateTransactions(writer, userAccounts);
    generateInvoices(writer, userIds);
    generateLoans(writer, userIds);
  }

  private List<Long> generateUsers(BufferedWriter writer) throws IOException {
    log.info("Generating {} users...", TOTAL_USERS);
    List<Long> userIds = new ArrayList<>();
    StringBuilder batch = new StringBuilder();
    int count = 0;

    for (long i = 1; i <= TOTAL_USERS; i++) {
      userIds.add(i);

      String name = faker.name().fullName().replace("'", "''");
      String email = faker.internet().emailAddress();
      String password = BCrypt.hashpw("password", BCrypt.gensalt());
      int age = random.nextInt(18, 80);
      double monthlyIncome = random.nextDouble() * 150000 + 30000;
      int creditScore = random.nextInt(300, 850);
      Role role = Role.values()[random.nextInt(Role.values().length)];

      batch.append(
          String.format(
              "INSERT INTO users (id, name, email, password, age, monthly_income, credit_score, role) "
                  + "VALUES (%d, '%s', '%s', '%s', %d, %.2f, %d, '%s');%s",
              i, name, email, password, age, monthlyIncome, creditScore, role, NEW_LINE));

      if (++count >= BATCH_SIZE) {
        writer.write(batch.toString());
        batch.setLength(0);
        count = 0;
        log.info("Generated {} users", i);
      }
    }

    if (!batch.isEmpty()) {
      writer.write(batch.toString());
    }

    writer.write(NEW_LINE);
    return userIds;
  }

  private Map<Long, List<Long>> generateAccounts(BufferedWriter writer, List<Long> userIds)
      throws IOException {
    log.info("Generating accounts for {} users...", userIds.size());
    Map<Long, List<Long>> userAccounts = new HashMap<>();
    StringBuilder batch = new StringBuilder();
    int count = 0;
    long accountId = 1;

    for (Long userId : userIds) {
      List<Long> accounts = new ArrayList<>();
      for (int i = 0; i < ACCOUNTS_PER_USER; i++) {
        accounts.add(accountId);

        double balance = random.nextDouble() * 50000 + 1000;
        AccountStatus status =
            AccountStatus.values()[random.nextInt(AccountStatus.values().length)];

        batch.append(
            String.format(
                "INSERT INTO accounts (id, balance, status, user_id) "
                    + "VALUES (%d, %.2f, '%s', %d);%s",
                accountId, balance, status, userId, NEW_LINE));

        if (++count >= BATCH_SIZE) {
          writer.write(batch.toString());
          batch.setLength(0);
          count = 0;
          log.info("Generated {} accounts", accountId);
        }

        accountId++;
      }
      userAccounts.put(userId, accounts);
    }

    if (!batch.isEmpty()) {
      writer.write(batch.toString());
    }

    writer.write(NEW_LINE);
    return userAccounts;
  }

  private void generateTransactions(BufferedWriter writer, Map<Long, List<Long>> userAccounts)
      throws IOException {
    log.info("Generating transactions...");
    StringBuilder batch = new StringBuilder();
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

          TransactionType type =
              TransactionType.values()[random.nextInt(TransactionType.values().length)];
          double amount = random.nextDouble() * 1000 + 10;
          TransactionStatus status =
              TransactionStatus.values()[random.nextInt(TransactionStatus.values().length)];

          batch.append(
              String.format(
                  "INSERT INTO transactions (id, type, amount, source_account_id, destination_account_id, status) "
                      + "VALUES (%d, '%s', %.2f, %d, %d, '%s');%s",
                  transactionId++,
                  type,
                  amount,
                  sourceAccountId,
                  destinationAccountId,
                  status,
                  NEW_LINE));

          if (++count >= BATCH_SIZE) {
            writer.write(batch.toString());
            batch.setLength(0);
            count = 0;
            log.info("Generated {} transactions", transactionId - 1);
          }
        }
      }
    }

    if (!batch.isEmpty()) {
      writer.write(batch.toString());
    }

    writer.write(NEW_LINE);
  }

  private void generateInvoices(BufferedWriter writer, List<Long> userIds) throws IOException {
    log.info("Generating invoices...");
    StringBuilder batch = new StringBuilder();
    int count = 0;
    long invoiceId = 1;

    for (Long userId : userIds) {
      for (int i = 0; i < INVOICES_PER_USER; i++) {
        double amountDue = random.nextDouble() * 5000 + 100;
        LocalDate dueDate = LocalDate.now().plusDays(random.nextInt(365));

        batch.append(
            String.format(
                "INSERT INTO invoices (id, amount_due, due_date, user_id) "
                    + "VALUES (%d, %.2f, '%s', %d);%s",
                invoiceId++, amountDue, dueDate, userId, NEW_LINE));

        if (++count >= BATCH_SIZE) {
          writer.write(batch.toString());
          batch.setLength(0);
          count = 0;
          log.info("Generated {} invoices", invoiceId - 1);
        }
      }
    }

    if (!batch.isEmpty()) {
      writer.write(batch.toString());
    }

    writer.write(NEW_LINE);
  }

  private void generateLoans(BufferedWriter writer, List<Long> userIds) throws IOException {
    log.info("Generating loans...");
    StringBuilder batch = new StringBuilder();
    int count = 0;
    long loanId = 1;

    for (Long userId : userIds) {
      for (int i = 0; i < LOANS_PER_USER; i++) {
        double principal = random.nextDouble() * 500000 + 10000;
        double interestRate = random.nextDouble() * 15 + 5;
        int termMonths = random.nextInt(12, 360);
        boolean approved = random.nextBoolean();

        batch.append(
            String.format(
                "INSERT INTO loans (id, principal, interest_rate, term_months, user_id, approved) "
                    + "VALUES (%d, %.2f, %.2f, %d, %d, %b);%s",
                loanId++, principal, interestRate, termMonths, userId, approved, NEW_LINE));

        if (++count >= BATCH_SIZE) {
          writer.write(batch.toString());
          batch.setLength(0);
          count = 0;
          log.info("Generated {} loans", loanId - 1);
        }
      }
    }

    if (!batch.isEmpty()) {
      writer.write(batch.toString());
    }

    writer.write(NEW_LINE);
  }
}

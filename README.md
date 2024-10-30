# Banking Data Generator

This Spring Boot application generates realistic test data for a banking system, producing an SQL file containing 12 million rows of interrelated data across multiple tables. This application can be customized by changing the data generation logic, schema, and relationships. 

## Table Structure
The generator creates the following tables with their relationships:

```
User (1M rows)
  └── Account (2M rows, 2 per user)
       └── Transaction (12M rows, 6 per account)
  └── Invoice (1M rows, 1 per user)
  └── Loan (1M rows, 1 per user)
```

### Entity Details
- **Users**: Basic customer information (name, age, income, credit score)
- **Accounts**: Banking accounts with balance and status
- **Transactions**: Money transfers between accounts
- **Invoices**: Bills due for payment
- **Loans**: Loan applications and status

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- IntelliJ IDEA (recommended) or any Java IDE
- At least 16GB of RAM (recommended for full dataset generation)
- Sufficient disk space (~10GB for the output file)

## Configuration

The application can be configured through `application.properties`:

```properties
# Choose generator type: 'db' for direct database insertion or 'file' for SQL file generation
generator.type=db

# Database Configuration (for db generator)
spring.datasource.url=jdbc:postgresql://localhost:5432/bankingdb
spring.datasource.username=postgres
spring.datasource.password=postgres

# File Configuration (for file generator)
generator.output.file=banking_data.sql

# Logging Configuration
logging.level.ma.hmzelidrissi.datagenerator=INFO
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

## Running the Application

### Using IntelliJ IDEA:
1. Open the project in IntelliJ IDEA
2. Wait for Maven to download dependencies
3. Configure database connection in `application.properties`
4. Right-click on `DataGeneratorApplication.java`
5. Select "Run 'DataGeneratorApplication'"

### Using Maven:
```bash
# For database generator
./mvnw spring-boot:run -Dgenerator.type=db

# For file generator
./mvnw spring-boot:run -Dgenerator.type=file
```

## Generated Data Specifications

### Users
- Realistic names using Java Faker
- Age: 18-80 years
- Monthly Income: $30,000-$180,000
- Credit Score: 300-850
- Roles: ADMIN, USER, EMPLOYEE

### Accounts
- Balance: $1,000-$51,000
- Status: ACTIVE, BLOCKED
- 2 accounts per user

### Transactions
- Amount: $10-$1,010
- Types: STANDARD, INSTANT
- Status: PENDING, COMPLETED, REJECTED
- 6 transactions per account

### Invoices
- Amount Due: $100-$5,100
- Due Date: Random date within next year
- 1 invoice per user

### Loans
- Principal: $10,000-$510,000
- Interest Rate: 5%-20%
- Term: 12-360 months
- 1 loan per user

## Performance Considerations

The application uses several optimization techniques:
- Batch processing (1000 records per batch)
- StringBuilder for string concatenation
- Efficient memory management
- Progress logging for monitoring

## Database Schema

The generated SQL file includes:
1. Table creation statements with proper constraints
2. Foreign key relationships
3. Appropriate indexes for better query performance
4. Data insertion statements in batches

## Importing Data

To import the generated data:

```bash
# MySQL
mysql -u username -p database_name < data.sql

# PostgreSQL
psql -U username -d database_name -f data.sql
```

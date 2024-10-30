package ma.hmzelidrissi.datagenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class DataGeneratorApplication implements CommandLineRunner {

    private final ApplicationContext context;
    private final PostgreSQLDataGenerator dbGenerator;
    private final SQLFileGenerator fileGenerator;

    @Value("${generator.type:db}")
    private String generatorType;

    public static void main(String[] args) {
        SpringApplication.run(DataGeneratorApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting data generation using {} generator", generatorType);

        switch (generatorType.toLowerCase()) {
            case "file" -> fileGenerator.run(args);
            case "db" -> dbGenerator.run(args);
            default -> throw new IllegalArgumentException("Invalid generator type: " + generatorType +
                    ". Valid values are 'file' or 'db'");
        }
    }
}
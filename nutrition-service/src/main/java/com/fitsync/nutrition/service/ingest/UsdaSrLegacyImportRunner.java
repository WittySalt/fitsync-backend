package com.fitsync.nutrition.service.ingest;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fitsync.nutrition.service.ingest.UsdaSrLegacyImporter.ImportSummary;
import com.fitsync.nutrition.service.ingest.UsdaSrLegacyImporter.SrLegacyFood;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-time loader for USDA SR Legacy: on startup, if {@code fitsync.nutrition.usda-import.directory} is
 * set, parse {@code food.csv} + {@code food_category.csv} from that directory and upsert into the catalog.
 * Guarded by {@link ConditionalOnProperty} so it is a complete no-op (the bean is not even created) unless
 * the directory is configured — normal app startup and tests are unaffected.
 */
@Component
@ConditionalOnProperty(prefix = "fitsync.nutrition.usda-import", name = "directory")
public class UsdaSrLegacyImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsdaSrLegacyImportRunner.class);

    private final UsdaSrLegacyCsvParser parser;
    private final UsdaSrLegacyImporter importer;
    private final String directory;

    public UsdaSrLegacyImportRunner(UsdaSrLegacyCsvParser parser,
                                    UsdaSrLegacyImporter importer,
                                    @Value("${fitsync.nutrition.usda-import.directory}") String directory) {
        this.parser = parser;
        this.importer = importer;
        this.directory = directory;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path dir = Path.of(directory);
        log.info("USDA SR Legacy import starting from {}", dir);
        try (Reader food = Files.newBufferedReader(dir.resolve("food.csv"), StandardCharsets.UTF_8);
             Reader category = Files.newBufferedReader(dir.resolve("food_category.csv"),
                     StandardCharsets.UTF_8)) {
            List<SrLegacyFood> foods = parser.parse(food, category);
            ImportSummary summary = importer.importFoods(foods);
            log.info("USDA SR Legacy import from {} complete: {}", dir, summary);
        } catch (IOException e) {
            log.error("USDA SR Legacy import from {} failed", dir, e);
        }
    }
}

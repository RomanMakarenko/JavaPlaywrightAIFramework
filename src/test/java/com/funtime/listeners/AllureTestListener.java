package com.funtime.listeners;

import com.funtime.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Writes Allure's {@code environment.properties} once per suite (browser, target app, OS, JVM),
 * so the report Overview page shows what the run was executed against. The file lands in the
 * configured Allure results directory and is read by the report generator.
 *
 * <p>Note: this cannot use {@code Allure.addAttachment(...)} — at {@code @BeforeSuite} time no
 * test is running yet, so attachments fail with "no test is running". Environment info is
 * exactly what {@code environment.properties} is for (extended with app build data in Phase 7).
 */
public class AllureTestListener implements ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(AllureTestListener.class);

    private static final String ALLURE_RESULTS_DIR_KEY = "allure.results.directory";
    private static final String DEFAULT_RESULTS_DIR = "target/allure-results";

    @Override
    public void onStart(ISuite suite) {
        writeEnvironmentProperties();
    }

    @Override
    public void onFinish(ISuite suite) {
        // nothing to tear down
    }

    private void writeEnvironmentProperties() {
        Path resultsDir = Path.of(resolveResultsDir());
        Path envFile = resultsDir.resolve("environment.properties");
        try {
            Files.createDirectories(resultsDir);
            Properties env = new Properties();
            env.setProperty("browser", ConfigReader.getBrowser());
            env.setProperty("headless", Boolean.toString(ConfigReader.getHeadless()));
            env.setProperty("base.url", ConfigReader.getBaseUrl());
            env.setProperty("os.name", System.getProperty("os.name"));
            env.setProperty("os.arch", System.getProperty("os.arch"));
            env.setProperty("java.version", System.getProperty("java.version"));
            try (var out = Files.newOutputStream(envFile)) {
                env.store(out, "Written by AllureTestListener — extend with app build info in CI (Phase 7)");
            }
        } catch (IOException e) {
            LOG.warn("Could not write Allure environment.properties to {}", envFile, e);
        }
    }

    /** Reads {@code allure.results.directory} from the test classpath, else the default. */
    private String resolveResultsDir() {
        String dir = DEFAULT_RESULTS_DIR;
        try (InputStream in = AllureTestListener.class.getClassLoader().getResourceAsStream("allure.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String value = props.getProperty(ALLURE_RESULTS_DIR_KEY);
                if (value != null && !value.isBlank()) {
                    dir = value;
                }
            }
        } catch (IOException e) {
            LOG.debug("allure.properties unreadable — using default results dir", e);
        }
        return dir;
    }
}
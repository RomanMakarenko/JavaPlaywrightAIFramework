package com.funtime.listeners;

import com.funtime.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.lang.reflect.Method;

/**
 * Re-runs a failed test up to a configured number of times, isolating flaky tests without
 * hiding real bugs. The budget comes from {@link ConfigReader#getRetries()} (env {@code RETRIES}),
 * or from an explicit {@link Retry @Retry(retries = N)} annotation on the method.
 *
 * <p>{@code retry()} is asked once per failed run; it grants another attempt while the number
 * of retries already given is below the budget. With the default {@code retries=0} nothing is
 * ever retried, so flakiness is opt-in per test or per run.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(RetryAnalyzer.class);

    private int retriesGiven = 0;

    @Override
    public boolean retry(ITestResult result) {
        int budget = resolveRetryBudget(result);
        if (budget <= 0 || retriesGiven >= budget) {
            return false;
        }
        retriesGiven++;
        LOG.warn("Retrying {}.{} (retry {}/{}) after failure",
                result.getTestClass().getName(), result.getName(), retriesGiven, budget);
        return true;
    }

    /** The number of extra attempts allowed for this method: annotation wins, else config. */
    private int resolveRetryBudget(ITestResult result) {
        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        Retry retry = method.getAnnotation(Retry.class);
        if (retry != null) {
            return retry.retries();
        }
        return ConfigReader.getRetries();
    }
}
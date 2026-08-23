package com.funtime.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Attaches {@link RetryAnalyzer} to every {@code @Test} so retries are driven by config
 * ({@code RETRIES}) and the {@link Retry @Retry} marker — no per-test boilerplate. Registered
 * in {@code testng.xml} alongside the other listeners.
 */
public class RetryListener implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
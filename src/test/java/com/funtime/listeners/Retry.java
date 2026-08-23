package com.funtime.listeners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-in marker for {@link RetryAnalyzer}: a method annotated {@code @Retry(retries = 2)} is
 * allowed up to two extra attempts after the first failure. Without the annotation, retries
 * come from the framework config ({@code RETRIES} / {@code retries}, default {@code 0}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Retry {

    /** Extra attempts after the first failure; must be {@code >= 0}. */
    int retries() default 0;
}
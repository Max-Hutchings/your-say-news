package com.yoursay.user.usercharacteristic.service;

import com.yoursay.platform.observability.DomainMetrics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncomeProfileCatalogObservabilityTest {

    private EntityManager entityManager;
    private DomainMetrics metrics;
    private IncomeProfileCatalog catalog;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        metrics = mock(DomainMetrics.class);
        catalog = new IncomeProfileCatalog();
        catalog.entityManager = entityManager;
        catalog.metrics = metrics;
    }

    @Test
    void recordsUnknownDraftAsAnExpectedActivationRejection() {
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> catalog.activate("GB-GBP-UNKNOWN-v9"));

        verify(metrics).recordOperation(
                eq("usercharacteristic"), eq("activate_profile"), eq("expected_rejection"),
                eq("validation"), eq("income_profile_activation_rejected"), anyLong());
    }

    @Test
    void recordsUnexpectedIllegalStateAsAServiceFailure() {
        when(entityManager.createNativeQuery(anyString()))
                .thenThrow(new IllegalStateException("unexpected workflow failure"));

        ExtLogRecord failure = captureFailure(() ->
            assertThrows(IllegalStateException.class,
                    () -> catalog.activate("GB-GBP-GROSS-2027-v2")));

        verify(metrics).recordOperation(
                eq("usercharacteristic"), eq("activate_profile"), eq("service_error"),
                eq("internal"), eq("income_profile_internal_failure"), anyLong());
        assertEquals("Income profile operation failed", failure.getMessage());
        assertEquals("usercharacteristic", failure.getMdc("domain"));
        assertEquals("activate_profile", failure.getMdc("operation"));
        assertEquals("service_error", failure.getMdc("outcome"));
        assertEquals("internal", failure.getMdc("error_type"));
        assertEquals("income_profile_internal_failure", failure.getMdc("error_code"));
    }

    @Test
    void recordsUnexpectedIllegalArgumentAsAServiceFailure() {
        when(entityManager.createNativeQuery(anyString()))
                .thenThrow(new IllegalArgumentException("unexpected argument failure"));

        assertThrows(IllegalArgumentException.class,
                () -> catalog.activate("GB-GBP-GROSS-2027-v2"));

        verify(metrics).recordOperation(
                eq("usercharacteristic"), eq("activate_profile"), eq("service_error"),
                eq("internal"), eq("income_profile_internal_failure"), anyLong());
    }

    @Test
    void recordsPersistenceFailureAsADependencyFailure() {
        when(entityManager.createNativeQuery(anyString()))
                .thenThrow(new PersistenceException("database unavailable"));

        ExtLogRecord failure = captureFailure(() -> assertThrows(
                PersistenceException.class, () -> catalog.activate("GB-GBP-GROSS-2027-v2")));

        verify(metrics).recordOperation(
                eq("usercharacteristic"), eq("activate_profile"), eq("dependency_error"),
                eq("database"), eq("income_profile_database_failure"), anyLong());
        assertEquals("dependency_error", failure.getMdc("outcome"));
        assertEquals("database", failure.getMdc("error_type"));
        assertEquals("income_profile_database_failure", failure.getMdc("error_code"));
    }

    @Test
    void recognisesPersistenceFailureWrappedByTheDataLayer() {
        RuntimeException wrapped = new RuntimeException(
                "data access failed", new PersistenceException("database unavailable"));
        when(entityManager.createNativeQuery(anyString())).thenThrow(wrapped);

        assertThrows(RuntimeException.class, () -> catalog.activate("GB-GBP-GROSS-2027-v2"));

        verify(metrics).recordOperation(
                eq("usercharacteristic"), eq("activate_profile"), eq("dependency_error"),
                eq("database"), eq("income_profile_database_failure"), anyLong());
    }

    private static ExtLogRecord captureFailure(Runnable action) {
        CapturingLogHandler logs = new CapturingLogHandler();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(logs);
        try {
            action.run();
        } finally {
            rootLogger.removeHandler(logs);
        }
        return assertInstanceOf(ExtLogRecord.class, logs.failure);
    }

    private static final class CapturingLogHandler extends Handler {
        private LogRecord failure;

        @Override
        public void publish(LogRecord record) {
            if ("Income profile operation failed".equals(record.getMessage())) {
                if (record instanceof ExtLogRecord extended) {
                    extended.copyMdc();
                }
                failure = record;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}

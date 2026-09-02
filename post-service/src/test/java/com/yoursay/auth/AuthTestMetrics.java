package com.yoursay.user.auth;

import com.yoursay.platform.observability.DomainMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import java.lang.reflect.Field;

final class AuthTestMetrics {

    private AuthTestMetrics() {
    }

    static DomainMetrics create(MeterRegistry registry) {
        DomainMetrics metrics = new DomainMetrics();
        try {
            setField(metrics, "registry", registry);
            setField(metrics, "environment", "test");
            return metrics;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create test metrics", exception);
        }
    }

    private static void setField(DomainMetrics target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = DomainMetrics.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

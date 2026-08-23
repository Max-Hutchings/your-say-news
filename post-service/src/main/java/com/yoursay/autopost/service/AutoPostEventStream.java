package com.yoursay.autopost.service;

import com.yoursay.autopost.dto.AutoPostEventDto;
import com.yoursay.autopost.dto.AutoPostRunDto;
import com.yoursay.autopost.observability.AutoPostLog;
import com.yoursay.observability.DomainMetrics;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Streams run snapshots until the current workflow phase reaches a reviewable state. */
@ApplicationScoped
public class AutoPostEventStream {

    @Inject
    DomainMetrics metrics;

    public Multi<AutoPostEventDto> openRunEventStream(Supplier<AutoPostRunDto> currentRun) {
        long streamStarted = System.nanoTime();
        AtomicBoolean streamOutcomeRecorded = new AtomicBoolean();
        Multi<AutoPostRunDto> updates = pollUntilStreamTerminal(currentRun);

        return appendTerminalState(updates, currentRun)
                .map(this::toMeasuredEvent)
                .onCompletion().invoke(() -> recordStreamOutcome(
                        streamOutcomeRecorded, "success", "none", "none", streamStarted))
                .onCancellation().invoke(() -> recordStreamOutcome(
                        streamOutcomeRecorded, "success", "none", "none", streamStarted))
                .onFailure().invoke(error -> {
                    recordStreamOutcome(streamOutcomeRecorded, "fault", "application",
                            "AUTO_POST_SSE_FAILED", streamStarted);
                    AutoPostLog.failed("sseLifetime", "event_stream", "application",
                            "AUTO_POST_SSE_FAILED", error);
                });
    }

    private Multi<AutoPostRunDto> pollUntilStreamTerminal(Supplier<AutoPostRunDto> currentRun) {
        return Multi.createBy().repeating().uni(() -> pollRun(currentRun))
                .withDelay(Duration.ofSeconds(1))
                .until(run -> run.status().streamTerminal());
    }

    private Uni<AutoPostRunDto> pollRun(Supplier<AutoPostRunDto> currentRun) {
        return Uni.createFrom()
                .item(currentRun)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Multi<AutoPostRunDto> appendTerminalState(
            Multi<AutoPostRunDto> updates,
            Supplier<AutoPostRunDto> currentRun
    ) {
        return updates.onCompletion().switchTo(
                () -> Multi.createFrom().uni(pollRun(currentRun)));
    }

    private AutoPostEventDto toMeasuredEvent(AutoPostRunDto run) {
        long started = System.nanoTime();
        try {
            AutoPostEventDto event = new AutoPostEventDto(run);
            recordOperation("sseEvent", "success", "none", "none", started);
            return event;
        } catch (RuntimeException error) {
            recordOperation("sseEvent", "fault", "application",
                    "AUTO_POST_SSE_EVENT_FAILED", started);
            AutoPostLog.failed("sseEvent", "event_serialization", "application",
                    "AUTO_POST_SSE_EVENT_FAILED", error);
            throw error;
        }
    }

    private void recordStreamOutcome(
            AtomicBoolean recorded,
            String outcome,
            String faultType,
            String faultCode,
            long started
    ) {
        if (recorded.compareAndSet(false, true)) {
            recordOperation("sseLifetime", outcome, faultType, faultCode, started);
        }
    }

    private void recordOperation(
            String operation,
            String outcome,
            String errorType,
            String errorCode,
            long started
    ) {
        metrics.recordOperation("autopost", operation, outcome, errorType, errorCode,
                System.nanoTime() - started);
    }
}

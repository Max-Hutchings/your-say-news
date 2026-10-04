package com.yoursay.platform.mobiletelemetry.service;

import com.yoursay.platform.mobiletelemetry.MobileTelemetryService;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryBatchDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryEventDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryReceiptDto;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.dto.UserAccessDto;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
class MobileTelemetryServiceImpl implements MobileTelemetryService {

    @Inject
    MobileTelemetryMetrics metrics;

    @Inject
    OtlpExporter exporter;

    @Inject
    YourSayUserService userService;

    @ConfigProperty(name = "app.environment")
    String environment;

    private MobileEventSanitizer sanitizer;
    private OtlpPayloadBuilder payloadBuilder;

    @PostConstruct
    void init() {
        Clock clock = Clock.systemUTC();
        sanitizer = new MobileEventSanitizer(clock);
        payloadBuilder = new OtlpPayloadBuilder(environment, clock);
    }

    @Override
    public MobileTelemetryReceiptDto record(MobileTelemetryBatchDto batch, String callerEmail) {
        MobileSession session = sanitizer.session(batch.sessionId(), batch.client())
                .withUserId(internalUserId(callerEmail));
        List<MobileEvent> events = sanitizeEvents(batch.events());
        int dropped = batch.events().size() - events.size();

        events.forEach(event -> recordMetrics(session, event));
        exportJourney(session, events);
        reportDroppedEvents(dropped);
        return new MobileTelemetryReceiptDto(events.size(), dropped);
    }

    /** The account's primary key, so support can find a reported user's journey. Never the email. */
    private Long internalUserId(String callerEmail) {
        UserAccessDto access = userService.getAccessByEmail(callerEmail);
        return access == null ? null : access.userId();
    }

    private List<MobileEvent> sanitizeEvents(List<MobileTelemetryEventDto> events) {
        return events.stream()
                .map(sanitizer::sanitize)
                .flatMap(Optional::stream)
                .toList();
    }

    private void recordMetrics(MobileSession session, MobileEvent event) {
        switch (event.type()) {
            case APP_START -> metrics.recordSessionStart(session);
            case SCREEN_ENTER -> metrics.recordScreenView(session, event);
            case SCREEN_EXIT -> metrics.recordScreenDwell(session, event);
            case ACTION -> metrics.recordAction(session, event);
            case API_CALL -> metrics.recordApiCall(session, event);
            case ERROR -> metrics.recordAppFault(session, event);
            case APP_STATE -> {
                // Lifecycle changes are journey context for logs only; there is nothing to count.
            }
        }
    }

    private void exportJourney(MobileSession session, List<MobileEvent> events) {
        if (events.isEmpty() || !exporter.isEnabled()) {
            return;
        }
        if (OtlpPayloadBuilder.hasSpans(events)) {
            exporter.export("traces", payloadBuilder.traces(session, events));
        }
        exporter.export("logs", payloadBuilder.logs(session, events));
    }

    /** Dropped events mean the app and this service disagree on the schema - worth a warning, not a fault. */
    private void reportDroppedEvents(int dropped) {
        if (dropped == 0) {
            return;
        }
        metrics.recordDroppedEvents(dropped);
        Log.warnf("domain=platform operation=record_mobile_telemetry outcome=error error_code=invalid_events dropped=%d",
                dropped);
    }
}

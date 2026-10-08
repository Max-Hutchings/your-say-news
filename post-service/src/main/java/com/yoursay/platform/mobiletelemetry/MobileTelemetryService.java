package com.yoursay.platform.mobiletelemetry;

import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryBatchDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryReceiptDto;

/** Turns app-reported events into metrics, spans and logs for the "your-say-news-mobile" service. */
public interface MobileTelemetryService {

    /**
     * @param callerEmail the authenticated uploader, used only to stamp the account's internal user
     *                    id on the journey (ADR-060). Never taken from the request body. Null for a
     *                    caller who has not signed in (ADR-061): the batch is kept without a user id,
     *                    within a service-wide anonymous budget.
     */
    MobileTelemetryReceiptDto record(MobileTelemetryBatchDto batch, String callerEmail);
}

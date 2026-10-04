package com.yoursay.platform.mobiletelemetry;

import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryBatchDto;
import com.yoursay.platform.mobiletelemetry.dto.MobileTelemetryReceiptDto;

/** Turns app-reported events into metrics, spans and logs for the "your-say-news-mobile" service. */
public interface MobileTelemetryService {

    /**
     * @param callerEmail the authenticated uploader, used only to stamp the account's internal user
     *                    id on the journey (ADR-060). Never taken from the request body.
     */
    MobileTelemetryReceiptDto record(MobileTelemetryBatchDto batch, String callerEmail);
}

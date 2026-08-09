package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceTrustPolicyTest {
    private final SourceTrustPolicy policy = new SourceTrustPolicy();

    @Test
    void classificationQualityDoesNotDependOnAHostAllowlist() {
        UnwrappedSourceDraftV1 source = new UnwrappedSourceDraftV1(
                "source-1", "https://www.bbc.com/news", "BBC", "Report",
                SourceClassification.OFFICIAL);

        assertTrue(policy.isHighQuality(source));
    }
}

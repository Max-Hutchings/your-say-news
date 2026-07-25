package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.UnwrappedSourceDraftV1;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceTrustPolicyTest {
    private final SourceTrustPolicy policy = new SourceTrustPolicy();

    @Test
    void rejectsAProviderSelfLabellingMediaAsOfficial() {
        UnwrappedSourceDraftV1 source = new UnwrappedSourceDraftV1(
                "source-1", "https://www.bbc.com/news", "BBC", "Report",
                SourceClassification.OFFICIAL);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> policy.validate(source));

        assertEquals("UNWRAPPED_SOURCE_CLASSIFICATION_INVALID", error.getMessage());
    }
}

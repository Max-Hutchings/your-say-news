package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SourceTrustPolicy {
    public boolean isHighQuality(UnwrappedSourceDraftV1 source) {
        return source.classification() == SourceClassification.OFFICIAL
                || source.classification() == SourceClassification.ACADEMIC;
    }
}

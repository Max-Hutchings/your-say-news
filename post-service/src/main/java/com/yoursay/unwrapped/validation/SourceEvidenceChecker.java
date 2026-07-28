package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;

public interface SourceEvidenceChecker {
    void verify(UnwrappedSourceDraftV1 source);
}

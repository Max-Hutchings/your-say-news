package com.yoursay.unwrapped.dto;

/** Administrator-facing progress state for an Unwrapped generation request. */
public enum UnwrappedGenerationState {
    NOT_STARTED,
    QUEUED,
    GENERATING,
    READY_FOR_REVIEW,
    FAILED
}

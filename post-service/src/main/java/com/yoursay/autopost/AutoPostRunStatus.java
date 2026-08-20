package com.yoursay.autopost;

public enum AutoPostRunStatus {
    QUEUED,
    DISCOVERING,
    CANDIDATES_READY,
    DRAFTING,
    DRAFT_READY,
    PUBLISHING,
    FAILED,
    PUBLISHED;

    public boolean streamTerminal() {
        return this == CANDIDATES_READY || this == DRAFT_READY || this == FAILED || this == PUBLISHED;
    }
}

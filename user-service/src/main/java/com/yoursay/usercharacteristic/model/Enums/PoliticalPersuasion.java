package com.yoursay.usercharacteristic.model.Enums;

/**
 * Political leaning as a broad left/right band (never party-specific). Named in CLAUDE.md as a core
 * breakdown axis. {@code NOT_POLITICAL} and {@code NOT_SURE} carry real signal (genuine states, not a
 * refusal — see ADR-016). Legacy {@code APOLITICAL} is kept so prior answers still parse.
 */
public enum PoliticalPersuasion {
    LEFT,
    CENTRE_LEFT,
    CENTRE,
    CENTRE_RIGHT,
    RIGHT,
    NOT_POLITICAL,
    NOT_SURE,
    // --- legacy (no longer offered) ---
    @Deprecated
    APOLITICAL
}

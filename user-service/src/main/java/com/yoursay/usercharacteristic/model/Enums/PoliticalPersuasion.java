package com.yoursay.usercharacteristic.model.Enums;

/**
 * Political leaning as a non-identifying band. Named explicitly in CLAUDE.md as a core breakdown
 * axis and likely the strongest predictor of how someone votes on a support question. Sensitive —
 * always optional and PNTS-able.
 */
public enum PoliticalPersuasion {
    LEFT,
    CENTRE_LEFT,
    CENTRE,
    CENTRE_RIGHT,
    RIGHT,
    APOLITICAL,
    PREFER_NOT_TO_SAY
}

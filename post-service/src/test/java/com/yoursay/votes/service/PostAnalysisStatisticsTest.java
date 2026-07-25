package com.yoursay.votes.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PostAnalysisStatisticsTest {
    @Test
    void benjaminiHochbergPinsSortedAndUnsortedAdjustedValues() {
        assertArrayEquals(new double[]{0.02, 0.04, 0.04, 0.008},
                PostAnalysisStatistics.benjaminiHochberg(List.of(0.01, 0.04, 0.03, 0.002)),
                0.000000000001);
    }

    @Test
    void smallExpectedCellsUseTwoSidedFisherRatherThanTheZApproximation() {
        PostAnalysisStatistics.TestResult result = PostAnalysisStatistics.compare(4, 5, 1, 10);
        assertEquals("FISHER_EXACT", result.method());
        assertEquals(0.01698301698301699, result.pValue(), 0.000000000000001);
    }

    @Test
    void aTinyRestGroupAlsoSelectsFisherEvenWhenTheCohortRowIsLarge() {
        PostAnalysisStatistics.TestResult result = PostAnalysisStatistics.compare(60, 100, 1, 1);
        assertEquals("FISHER_EXACT", result.method());
        assertEquals(1.0, result.pValue(), 0.000000000000001);
    }
}

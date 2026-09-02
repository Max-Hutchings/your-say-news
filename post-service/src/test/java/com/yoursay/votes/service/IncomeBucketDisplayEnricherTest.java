package com.yoursay.votes.service;

import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.user.usercharacteristic.IncomeRangeDisplayService;
import com.yoursay.votes.dto.BucketSentiment;
import com.yoursay.votes.dto.ChoiceSentiment;
import com.yoursay.votes.dto.SentimentBreakdownDto;
import com.yoursay.votes.error.VoteApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IncomeBucketDisplayEnricherTest {

    @Test
    void rejectsAnIncomeBucketThatHasNoPublishedCountryProfile() {
        IncomeBucketDisplayEnricher enricher = new IncomeBucketDisplayEnricher();
        enricher.incomeRanges = mock(IncomeRangeDisplayService.class);
        enricher.metrics = mock(DomainMetrics.class);
        SentimentBreakdownDto breakdown = new SentimentBreakdownDto(
                42L, null, "personalIncomeRange", List.of(),
                List.of(new BucketSentiment("V2_TIER_3", 40,
                        List.of(new ChoiceSentiment(101L, 20, 50.0)))), 0);

        VoteApiException error = assertThrows(VoteApiException.class,
                () -> enricher.enrich(breakdown));

        assertEquals("VOTE_INCOME_RANGE_UNRESOLVED", error.errorCode());
        assertEquals(500, error.statusCode());
        verify(enricher.metrics).recordOperation(
                eq("votes"), eq("enrich_income_display"), eq("service_error"),
                eq("data_integrity"), eq("VOTE_INCOME_RANGE_UNRESOLVED"), anyLong());
    }

    @Test
    void keepsTheUnknownBucketForVotesWithoutVersionedIncome() {
        IncomeBucketDisplayEnricher enricher = new IncomeBucketDisplayEnricher();
        enricher.incomeRanges = mock(IncomeRangeDisplayService.class);
        enricher.metrics = mock(DomainMetrics.class);
        BucketSentiment unknown = new BucketSentiment("UNKNOWN", 40,
                List.of(new ChoiceSentiment(101L, 20, 50.0)));
        SentimentBreakdownDto breakdown = new SentimentBreakdownDto(
                42L, null, "personalIncomeRange", List.of(), List.of(unknown), 0);

        assertEquals(List.of(unknown), enricher.enrich(breakdown).buckets());
        verify(enricher.metrics).recordOperation(
                eq("votes"), eq("enrich_income_display"), eq("success"), eq("none"),
                eq("none"), anyLong());
    }
}

package com.yoursay.votes;

import com.yoursay.votes.error.VoteApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which refusals are "normal" is a product decision, not an HTTP one. If this drifts, the error rate
 * on every dashboard either hides real faults or reports the product working as a fault.
 */
class VoteApiExceptionClassificationTest {

    @Test
    void treatsASecondVoteOnThePostAsTheProductRuleWorking() {
        VoteApiException duplicate = VoteApiException.duplicateVote(2007L, 42L);

        assertTrue(duplicate.expectedRejection());
        assertEquals(409, duplicate.statusCode());
        assertEquals("VOTE_DUPLICATE", duplicate.errorCode());
    }

    @Test
    void treatsLockedResultsAsTheProductRuleWorking() {
        VoteApiException locked = VoteApiException.resultsLocked(2007L);

        assertTrue(locked.expectedRejection());
        assertEquals(403, locked.statusCode());
        assertEquals("VOTE_RESULTS_LOCKED", locked.errorCode());
    }

    /** Error code and status are both a client contract and a metric tag, so both are pinned. */
    @Test
    void treatsEveryOtherVoteFailureAsARealErrorWithItsOwnStableCode() {
        assertRealError(VoteApiException.postMissing(2007L), 404, "VOTE_POST_MISSING");
        assertRealError(VoteApiException.invalidVote("optionId is required"), 400, "VOTE_INVALID");
        assertRealError(VoteApiException.optionNotAvailable(2007L, 9L), 400, "VOTE_OPTION_NOT_AVAILABLE");
        assertRealError(VoteApiException.unknownAxis("favourite_colour"), 400, "VOTE_UNKNOWN_AXIS");
        assertRealError(VoteApiException.userMissing("john.doe@example.com"), 401, "VOTE_USER_MISSING");
        assertRealError(VoteApiException.userLookupFailed("john.doe@example.com", 502),
                502, "VOTE_USER_LOOKUP_FAILED");
        assertRealError(VoteApiException.unresolvedIncomeRange(), 500, "VOTE_INCOME_RANGE_UNRESOLVED");
    }

    /** A non-standard upstream status has no JAX-RS constant, so it must fall back rather than fail. */
    @Test
    void fallsBackToBadGatewayWhenTheUserServiceReturnsANonStandardStatus() {
        assertEquals(502, VoteApiException.userLookupFailed("john.doe@example.com", 599).statusCode());
        assertEquals(503, VoteApiException.userLookupFailed("john.doe@example.com", 503).statusCode());
    }

    @Test
    void keepsTheVoterOutOfEveryPublicMessage() {
        assertEquals("The request conflicts with the current state.",
                VoteApiException.duplicateVote(2007L, 42L).publicMessage());
        // These two carry the caller's email in their detail message; it must not reach the client.
        assertEquals("Authentication is required.",
                VoteApiException.userMissing("john.doe@example.com").publicMessage());
        assertFalse(VoteApiException.userLookupFailed("john.doe@example.com", 502)
                .publicMessage().contains("@"));
    }

    private static void assertRealError(VoteApiException exception, int status, String errorCode) {
        assertFalse(exception.expectedRejection(), errorCode + " must count as an error");
        assertEquals(status, exception.statusCode(), errorCode + " status");
        assertEquals(errorCode, exception.errorCode());
    }
}

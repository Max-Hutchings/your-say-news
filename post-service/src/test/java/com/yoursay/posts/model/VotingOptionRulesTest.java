package com.yoursay.posts.model;

import com.yoursay.posts.VotingType;
import com.yoursay.posts.error.PostApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VotingOptionRulesTest {

    @Test
    void missingTypeBuildsTheFixedBinaryOptions() {
        List<VotingOptionRules.Definition> options = VotingOptionRules.normalize(null, null);

        assertEquals(2, options.size());
        assertEquals(new VotingOptionRules.Definition("Agree", 0, "AGREE"), options.get(0));
        assertEquals(new VotingOptionRules.Definition("Disagree", 1, "DISAGREE"), options.get(1));
    }

    @Test
    void binaryRejectsClientAuthoredOptions() {
        PostApiException error = assertThrows(PostApiException.class,
                () -> VotingOptionRules.normalize(VotingType.BINARY, List.of("Yes", "No")));

        assertEquals(400, error.getResponse().getStatus());
        assertEquals("POST_VOTE_OPTIONS_INVALID", error.errorCode());
    }

    @Test
    void multipleChoiceTrimsLabelsAndPreservesOrder() {
        List<VotingOptionRules.Definition> options = VotingOptionRules.normalize(
                VotingType.MULTIPLE_CHOICE,
                List.of("  More frequent buses ", "Protected cycle lanes", "Lower parking charges "));

        assertEquals(3, options.size());
        assertEquals(new VotingOptionRules.Definition("More frequent buses", 0, null), options.get(0));
        assertEquals(new VotingOptionRules.Definition("Protected cycle lanes", 1, null), options.get(1));
        assertEquals(new VotingOptionRules.Definition("Lower parking charges", 2, null), options.get(2));
        assertNull(options.get(2).semanticKey());
    }

    @Test
    void multipleChoiceRequiresBetweenTwoAndFiveOptions() {
        assertInvalid(List.of("Only one"));
        assertInvalid(List.of("One", "Two", "Three", "Four", "Five", "Six"));
    }

    @Test
    void multipleChoiceRejectsBlankLongAndCaseInsensitiveDuplicateLabels() {
        assertInvalid(List.of("Valid choice", "   "));
        assertInvalid(List.of("A".repeat(121), "Valid choice"));
        assertInvalid(List.of("Protected cycle lanes", " protected CYCLE lanes "));
    }

    private static void assertInvalid(List<String> labels) {
        PostApiException error = assertThrows(PostApiException.class,
                () -> VotingOptionRules.normalize(VotingType.MULTIPLE_CHOICE, labels));
        assertEquals(400, error.getResponse().getStatus());
        assertEquals("POST_VOTE_OPTIONS_INVALID", error.errorCode());
    }
}

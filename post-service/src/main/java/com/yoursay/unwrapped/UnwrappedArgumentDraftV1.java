package com.yoursay.unwrapped;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record UnwrappedArgumentDraftV1(
        @Description("Existing option id copied exactly from the request") Long optionId,
        @Description("Concise persuasive headline") String headline,
        @Description("At most two cohort ids copied exactly from the supplied shortlist")
        List<String> usedCohortIds,
        @Description("Prediction-only cautious forecasts; empty for observed stories")
        List<String> predictedCohorts,
        @Description("Concise claim-level sourced context") List<UnwrappedClaimDraftV1> contextClaims,
        @Description("Charitable persuasive synthesis") String synthesis,
        @Description("Short association/sample caveat") String caveat
) {
}

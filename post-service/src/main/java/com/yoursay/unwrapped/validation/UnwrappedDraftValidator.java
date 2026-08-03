package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedClaimDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class UnwrappedDraftValidator {
    private final SourceUrlPolicy urlPolicy;

    @Inject
    public UnwrappedDraftValidator(SourceUrlPolicy urlPolicy) {
        this.urlPolicy = urlPolicy;
    }

    public void validate(UnwrappedResearchRequest request, UnwrappedResearchDraftV1 draft,
                         List<String> providerCitations) {
        require(draft != null, "UNWRAPPED_DRAFT_MISSING");
        require(draft.pages() != null && draft.pages().size() == request.options().size(),
                "UNWRAPPED_OPTION_PAGE_COUNT");
        for (int index = 0; index < request.options().size(); index++) {
            OptionBriefV1 brief = request.options().get(index);
            UnwrappedArgumentDraftV1 page = draft.pages().get(index);
            require(page != null && brief.option().id().equals(page.optionId()),
                    "UNWRAPPED_OPTION_ORDER");
            require(length(page.headline(), 8, 140) && length(page.synthesis(), 40, 1400)
                            && length(page.caveat(), 10, 320),
                    "UNWRAPPED_PAGE_LENGTH");
            validateLanguage(page.headline());
            validateLanguage(page.synthesis());
            validateLanguage(page.caveat());
            require(page.contextClaims() != null && !page.contextClaims().isEmpty(),
                    "UNWRAPPED_PAGE_UNSOURCED");
            require(page.usedCohortIds() != null, "UNWRAPPED_COHORTS_MISSING");
            List<String> used = page.usedCohortIds();
            require(used.size() <= 2 && new HashSet<>(used).size() == used.size(),
                    "UNWRAPPED_TOO_MANY_COHORTS");
            Set<String> allowed = brief.candidates().stream()
                    .map(candidate -> candidate.cohortId()).collect(Collectors.toSet());
            require(allowed.containsAll(used), "UNWRAPPED_INVENTED_COHORT");
            require(containsAny(page.caveat(), "association", "sample", "voters on this post",
                            "people who voted on this post")
                    && (containsAny(page.caveat(), "does not", "do not", "cannot", "not prove")
                            || containsAny(page.caveat(), "only people who voted on this post")),
                    "UNWRAPPED_OBSERVED_CAVEAT");
        }

        List<UnwrappedSourceDraftV1> sources = draft.sources() == null ? List.of() : draft.sources();
        Map<String, UnwrappedSourceDraftV1> byId = uniqueSources(sources);
        for (UnwrappedSourceDraftV1 source : sources) {
            require(length(source.publisher(), 2, 256) && length(source.title(), 4, 512)
                            && source.classification() != null,
                    "UNWRAPPED_SOURCE_METADATA");
            urlPolicy.validate(source.url());
        }
        for (UnwrappedArgumentDraftV1 page : draft.pages()) {
            for (UnwrappedClaimDraftV1 claim :
                    page.contextClaims() == null ? List.<UnwrappedClaimDraftV1>of() : page.contextClaims()) {
                require(length(claim.statement(), 8, 700), "UNWRAPPED_CLAIM_LENGTH");
                validateLanguage(claim.statement());
                require(claim.sourceIds() != null
                                && claim.sourceIds().stream().allMatch(byId::containsKey),
                        "UNWRAPPED_CLAIM_UNSOURCED");
            }
        }
        validateBalancedLengths(draft.pages());
    }

    private static Map<String, UnwrappedSourceDraftV1> uniqueSources(List<UnwrappedSourceDraftV1> sources) {
        require(sources.stream().allMatch(source -> source != null && source.id() != null
                && !source.id().isBlank()), "UNWRAPPED_SOURCE_ID");
        Map<String, UnwrappedSourceDraftV1> result = sources.stream().collect(Collectors.toMap(
                UnwrappedSourceDraftV1::id, Function.identity(), (left, right) -> {
                    throw new IllegalArgumentException("UNWRAPPED_DUPLICATE_SOURCE_ID");
                }));
        return Map.copyOf(result);
    }

    private static void validateBalancedLengths(List<UnwrappedArgumentDraftV1> pages) {
        if (pages.size() < 2) return;
        int shortest = pages.stream().mapToInt(UnwrappedDraftValidator::contentLength).min().orElse(1);
        int longest = pages.stream().mapToInt(UnwrappedDraftValidator::contentLength).max().orElse(1);
        require(shortest > 0 && longest <= shortest * 2.5, "UNWRAPPED_UNBALANCED_PAGES");
    }

    private static int contentLength(UnwrappedArgumentDraftV1 page) {
        int claims = page.contextClaims() == null ? 0 : page.contextClaims().stream()
                .map(UnwrappedClaimDraftV1::statement).filter(value -> value != null)
                .mapToInt(String::length).sum();
        return safeLength(page.headline()) + safeLength(page.synthesis())
                + safeLength(page.caveat()) + claims;
    }

    private static boolean length(String value, int minimum, int maximum) {
        return value != null && value.trim().length() >= minimum && value.trim().length() <= maximum;
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static void validateLanguage(String value) {
        if (value == null) return;
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        boolean causal = containsUnqualifiedCausalClaim(normalized);
        boolean representative = POPULATION_LANGUAGE.matcher(normalized).find();
        require(!causal, "UNWRAPPED_CAUSAL_INFERENCE");
        require(!representative, "UNWRAPPED_POPULATION_INFERENCE");
    }

    private static boolean containsUnqualifiedCausalClaim(String normalized) {
        for (String clause : normalized.split("\\b(?:but|however|although)\\b|[,.;]")) {
            Matcher matcher = CAUSAL_LANGUAGE.matcher(clause);
            while (matcher.find()) {
                String prefix = clause.substring(0, matcher.start()).stripTrailing();
                boolean negated = prefix.endsWith("does not")
                        || prefix.endsWith("doesn't")
                        || prefix.endsWith("cannot")
                        || prefix.endsWith("can't")
                        || prefix.endsWith("is not evidence that");
                if (!negated) return true;
            }
        }
        return false;
    }

    private static final Pattern CAUSAL_LANGUAGE = Pattern.compile(
            "(caus(?:e|ed|es)|prov(?:e|ed|es)|made|drove|led|because)"
                    + ".{0,100}(vote|voted|chose|choice|support|oppose)"
                    + "|(men|women|people|voters|group|cohort|demographic|audience|gender)"
                    + ".{0,100}(because|therefore|caus(?:e|ed|es)|made|drove|led)");
    private static final Pattern POPULATION_LANGUAGE = Pattern.compile(
            "(representative of|represents|mirrors|reflects).{0,60}"
                    + "(the )?(population|national opinion|public opinion|everyone)"
                    + "|generali[sz](?:e|es|ed).{0,40}(population|public|national)");

    private static boolean containsAny(String value, String... phrases) {
        if (value == null) return false;
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return Arrays.stream(phrases).anyMatch(normalized::contains);
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}

package com.yoursay.posts.postagent.generator;

import com.yoursay.posts.postagent.dto.AgentDraftDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.SourcedClaimDto;
import com.yoursay.posts.postagent.dto.AgentVoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.posts.model.VotingOptionRules;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.text.BreakIterator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class AgentDraftValidator {
    private static final int SUMMARY_CLAIM_COUNT = 3;
    private static final int CASE_FOR_CLAIM_COUNT = 2;
    private static final int CASE_AGAINST_CLAIM_COUNT = 2;
    private static final int MAXIMUM_CLAIM_WORDS = 30;
    private static final int MAXIMUM_QUESTION_WORDS = 20;
    private static final int MINIMUM_SOURCE_COUNT = 2;
    private static final int MAXIMUM_SOURCE_COUNT = 6;
    private static final int MAXIMUM_SOURCE_URL_LENGTH = 2_048;
    private static final int MAXIMUM_SOURCE_TITLE_WORDS = 18;
    private static final int MAXIMUM_SOURCE_TITLE_LENGTH = 512;
    private static final int MAXIMUM_PUBLISHER_WORDS = 6;
    private static final int MAXIMUM_PUBLISHER_LENGTH = 256;
    private static final int MAXIMUM_VOTE_OPTION_WORDS = 6;
    private static final int MAXIMUM_VOTE_OPTION_LENGTH = 60;
    private static final int MAXIMUM_IMAGE_BRIEF_WORDS = 25;
    private static final int MINIMUM_IMAGE_SEARCH_WORDS = 3;
    private static final int MAXIMUM_IMAGE_SEARCH_WORDS = 8;

    public void validate(AgentDraftDto draft, List<String> providerCitations) {
        requireDraftShape(draft);
        validateSupportQuestion(draft.supportQuestion());
        validateVoting(draft.votingType(), draft.voteOptions());
        validateImageFields(draft.imageBrief(), draft.imageSearchQuery());

        Set<String> citations = canonicalUrls(providerCitations, "provider citations");
        Set<String> declaredSources = validateSources(draft.sources(), citations);
        Set<String> referencedSources = new HashSet<>();
        validateClaims("summaryClaims", draft.summaryClaims(), declaredSources, citations,
                referencedSources);
        validateClaims("caseForClaims", draft.caseForClaims(), declaredSources, citations,
                referencedSources);
        validateClaims("caseAgainstClaims", draft.caseAgainstClaims(), declaredSources, citations,
                referencedSources);
        require(referencedSources.containsAll(declaredSources),
                "AGENT_UNUSED_SOURCE", "Draft contains an unused source");
    }

    private static void requireDraftShape(AgentDraftDto draft) {
        require(draft != null, "AGENT_DRAFT_MISSING", "Provider returned no draft");
        requireSize(draft.summaryClaims(), SUMMARY_CLAIM_COUNT,
                "AGENT_SUMMARY_CLAIM_COUNT", "summaryClaims must contain exactly 3 claims");
        requireSize(draft.caseForClaims(), CASE_FOR_CLAIM_COUNT,
                "AGENT_CASE_FOR_CLAIM_COUNT", "caseForClaims must contain exactly 2 claims");
        requireSize(draft.caseAgainstClaims(), CASE_AGAINST_CLAIM_COUNT,
                "AGENT_CASE_AGAINST_CLAIM_COUNT", "caseAgainstClaims must contain exactly 2 claims");
    }

    private static void validateSupportQuestion(String question) {
        requireText(question, "supportQuestion");
        require(wordCount(question) <= MAXIMUM_QUESTION_WORDS && question.trim().endsWith("?"),
                "AGENT_SUPPORT_QUESTION_INVALID",
                "supportQuestion must contain at most 20 words and end with a question mark");
    }

    private static void validateImageFields(String imageBrief, String imageSearchQuery) {
        requireText(imageBrief, "imageBrief");
        require(singleSentence(imageBrief) && wordCount(imageBrief) <= MAXIMUM_IMAGE_BRIEF_WORDS,
                "AGENT_IMAGE_BRIEF_INVALID",
                "imageBrief must be one sentence of at most 25 words");
        requireText(imageSearchQuery, "imageSearchQuery");
        int searchWords = wordCount(imageSearchQuery);
        require(searchWords >= MINIMUM_IMAGE_SEARCH_WORDS
                        && searchWords <= MAXIMUM_IMAGE_SEARCH_WORDS,
                "AGENT_IMAGE_SEARCH_QUERY_WORDS",
                "imageSearchQuery must contain 3 to 8 words");
    }

    private static void validateVoting(VotingType type, List<AgentVoteOptionDto> options) {
        require(type != null, "AGENT_VOTING_TYPE_MISSING", "votingType is required");
        List<String> labels = options == null ? List.of() : options.stream()
                .map(option -> option == null ? null : option.label()).toList();
        require(labels.stream().allMatch(label -> label != null
                        && wordCount(label) >= 1
                        && wordCount(label) <= MAXIMUM_VOTE_OPTION_WORDS
                        && label.trim().length() <= MAXIMUM_VOTE_OPTION_LENGTH),
                "AGENT_VOTE_OPTION_LENGTH",
                "Vote options must contain 1 to 6 words and at most 60 characters");
        if (type == VotingType.BINARY) {
            require(labels.equals(List.of("Agree", "Disagree")),
                    "AGENT_BINARY_VOTE_OPTIONS",
                    "Binary drafts must contain fixed Agree and Disagree options");
            return;
        }
        try {
            VotingOptionRules.normalize(type, labels);
        } catch (RuntimeException error) {
            throw invalid("AGENT_VOTE_OPTIONS_INVALID",
                    "Invalid multiple-choice options: " + error.getMessage());
        }
    }

    private static Set<String> validateSources(List<AgentSourceDto> sources, Set<String> citations) {
        require(sources != null && sources.size() >= MINIMUM_SOURCE_COUNT
                        && sources.size() <= MAXIMUM_SOURCE_COUNT,
                "AGENT_SOURCE_COUNT", "Draft must contain two to six sources");
        Set<String> declared = new HashSet<>();
        for (AgentSourceDto source : sources) {
            require(source != null, "AGENT_SOURCE_MISSING", "Draft contains a null source");
            require(source.url() != null && source.url().length() <= MAXIMUM_SOURCE_URL_LENGTH,
                    "AGENT_SOURCE_URL_INVALID", "Source URL is missing or too long");
            String url = canonicalUrl(source.url());
            validateSourceMetadata(source);
            require(citations.contains(url), "AGENT_SOURCE_NOT_PROVIDER_CITATION",
                    "Draft source was not returned in provider citations: " + source.url());
            declared.add(url);
        }
        require(declared.size() == sources.size(), "AGENT_DUPLICATE_SOURCE",
                "Draft must contain distinct sources");
        return declared;
    }

    private static void validateSourceMetadata(AgentSourceDto source) {
        requireText(source.title(), "source title");
        require(wordCount(source.title()) <= MAXIMUM_SOURCE_TITLE_WORDS
                        && source.title().trim().length() <= MAXIMUM_SOURCE_TITLE_LENGTH,
                "AGENT_SOURCE_TITLE_INVALID", "Source title is too long");
        requireText(source.publisher(), "source publisher");
        require(wordCount(source.publisher()) <= MAXIMUM_PUBLISHER_WORDS
                        && source.publisher().trim().length() <= MAXIMUM_PUBLISHER_LENGTH,
                "AGENT_SOURCE_PUBLISHER_INVALID", "Source publisher is too long");
    }

    private static void validateClaims(String field, List<SourcedClaimDto> claims,
                                       Set<String> declaredSources, Set<String> citations,
                                       Set<String> referencedSources) {
        for (SourcedClaimDto claim : claims) {
            require(claim != null, "AGENT_CLAIM_MISSING", field + " contains a null claim");
            requireText(claim.text(), field + " text");
            require(singleSentence(claim.text()), "AGENT_CLAIM_SENTENCE_COUNT",
                    field + " claim must contain exactly one sentence");
            require(wordCount(claim.text()) <= MAXIMUM_CLAIM_WORDS,
                    "AGENT_CLAIM_WORDS", field + " claim exceeds 30 words");
            require(claim.sourceUrls() != null
                            && claim.sourceUrls().size() >= 1
                            && claim.sourceUrls().size() <= 2,
                    "AGENT_CLAIM_SOURCES", field + " claim must contain one or two sources");
            Set<String> claimSources = new HashSet<>();
            for (String rawUrl : claim.sourceUrls()) {
                String url = canonicalUrl(rawUrl);
                require(declaredSources.contains(url), "AGENT_CLAIM_SOURCE_UNDECLARED",
                        field + " claim cites an undeclared source: " + rawUrl);
                require(citations.contains(url), "AGENT_CLAIM_SOURCE_NOT_PROVIDER_CITATION",
                        field + " claim cites a URL absent from provider citations: " + rawUrl);
                claimSources.add(url);
            }
            require(claimSources.size() == claim.sourceUrls().size(),
                    "AGENT_CLAIM_SOURCE_DUPLICATE",
                    field + " claim contains a duplicate source");
            referencedSources.addAll(claimSources);
        }
    }

    private static Set<String> canonicalUrls(List<String> urls, String field) {
        if (urls == null || urls.isEmpty()) {
            throw invalid("AGENT_PROVIDER_CITATIONS_MISSING", field + " are empty");
        }
        Set<String> result = new HashSet<>();
        for (String url : urls) {
            result.add(canonicalUrl(url));
        }
        return result;
    }

    private static String canonicalUrl(String raw) {
        requireText(raw, "source URL");
        try {
            URI uri = URI.create(raw.trim()).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw invalid("AGENT_SOURCE_URL_INVALID", "Source URL must use HTTP(S): " + raw);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw invalid("AGENT_SOURCE_URL_INVALID", "Source URL has no host: " + raw);
            }
            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(scheme, uri.getUserInfo(), host.toLowerCase(Locale.ROOT), uri.getPort(),
                    path, uri.getQuery(), null).toString();
        } catch (GenerationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("AGENT_SOURCE_URL_INVALID", "Invalid source URL: " + raw);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid("AGENT_REQUIRED_FIELD", field + " is required");
        }
    }

    private static void requireSize(List<?> values, int size, String code, String message) {
        require(values != null && values.size() == size, code, message);
    }

    private static int wordCount(String value) {
        if (value == null || value.isBlank()) return 0;
        return value.trim().split("\\s+").length;
    }

    private static boolean singleSentence(String value) {
        if (value == null || value.isBlank()) return false;
        String text = value.trim();
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.UK);
        sentences.setText(text);
        int count = 0;
        for (int start = sentences.first(), end = sentences.next();
             end != BreakIterator.DONE;
             start = end, end = sentences.next()) {
            if (!text.substring(start, end).isBlank()) count++;
        }
        return count == 1;
    }

    private static void require(boolean condition, String code, String message) {
        if (!condition) throw invalid(code, message);
    }

    private static GenerationException invalid(String code, String message) {
        return new GenerationException(code, message, false);
    }
}

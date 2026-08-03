package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedClaimDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.validation.UnwrappedDraftValidator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;

/** LangChain4j implementation kept behind the provider-neutral Unwrapped domain interface. */
@ApplicationScoped
public class LangChain4jUnwrappedResearchGenerator implements UnwrappedResearchGenerator {
    private static final String NOT_CONFIGURED = "__not_configured__";

    @Inject
    UnwrappedResearchAiService aiService;

    @Inject
    UnwrappedChatResponseCapture responseCapture;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    UnwrappedDraftValidator validator;

    @ConfigProperty(name = "unwrapped.agent.api-key", defaultValue = "__not_configured__")
    String apiKey;

    @ConfigProperty(name = "unwrapped.agent.model", defaultValue = "configured-model")
    String configuredModel;

    @ConfigProperty(name = "unwrapped.agent.stubbed", defaultValue = "false")
    boolean stubbed;

    @Override
    public UnwrappedResearchResult generate(UnwrappedResearchRequest request) {
        if (stubbed) return stubbedResult(request);
        if (apiKey == null || apiKey.isBlank() || NOT_CONFIGURED.equals(apiKey)) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_NOT_CONFIGURED");
        }
        responseCapture.begin();
        try {
            UnwrappedResearchDraftV1 draft = aiService.research(researchPrompt(request));
            ChatResponse response = responseCapture.take();
            if (response == null || draft == null) {
                throw new IllegalStateException("UNWRAPPED_PROVIDER_RESPONSE_MISSING");
            }
            List<String> citations = citations(response);
            Log.infof("Unwrapped provider draft received: postId=%d expectedOptions=%s returnedOptions=%s citations=%d",
                    request.postId(), request.options().stream().map(option -> option.option().id()).toList(),
                    draft.pages() == null ? null : draft.pages().stream()
                            .map(page -> page == null ? null : page.optionId()).toList(), citations.size());
            try {
                validator.validate(request, draft, citations);
            } catch (IllegalArgumentException validationFailure) {
                Log.warnf(
                        "Unwrapped draft rejected: postId=%d code=%s pageDiagnostics=%s sourceDiagnostics=%s providerCitationCount=%d",
                        request.postId(), validationFailure.getMessage(), pageDiagnostics(draft),
                        sourceDiagnostics(draft), citations.size());
                throw validationFailure;
            }
            return new UnwrappedResearchResult(draft, citations,
                    valueOr(response.modelName(), configuredModel), response.id());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_FAILURE", e);
        } finally {
            responseCapture.clear();
        }
    }

    private static List<String> pageDiagnostics(UnwrappedResearchDraftV1 draft) {
        if (draft.pages() == null) return List.of("pages=null");
        return draft.pages().stream()
                .map(page -> page == null ? "page=null" : "optionId=" + page.optionId()
                        + ",cohorts=" + size(page.usedCohortIds())
                        + ",claims=" + size(page.contextClaims()))
                .toList();
    }

    private static List<String> sourceDiagnostics(UnwrappedResearchDraftV1 draft) {
        if (draft.sources() == null) return List.of("sources=null");
        return draft.sources().stream()
                .map(source -> source == null ? "source=null" : source.id() + "=" + source.url()
                        + "(" + source.classification() + ")")
                .toList();
    }

    private static int size(List<?> values) {
        return values == null ? -1 : values.size();
    }

    private UnwrappedResearchResult stubbedResult(UnwrappedResearchRequest request) {
        UnwrappedSourceDraftV1 source = new UnwrappedSourceDraftV1(
                "stub-source", "https://www.ons.gov.uk/", "Office for National Statistics",
                "Deterministic Unwrapped integration-test source", SourceClassification.OFFICIAL);
        List<UnwrappedArgumentDraftV1> pages = request.options().stream()
                .map(option -> new UnwrappedArgumentDraftV1(
                        option.option().id(),
                        "Development preview for " + option.option().label(),
                        option.candidates().stream()
                                .limit(2)
                                .map(candidate -> candidate.cohortId())
                                .toList(),
                        List.of(new UnwrappedClaimDraftV1(
                                "stub-claim-" + option.option().id(),
                                "Deterministic source-backed context for " + option.option().label() + ".",
                                List.of(source.id()), false)),
                        "This fixed development result represents " + option.overallVoteCount()
                                + " votes (" + option.overallVotePercentage()
                                + "%) for this option without calling an external model.",
                        "This association describes only people who voted on this post and does not represent any broader population."))
                .toList();
        return new UnwrappedResearchResult(
                new UnwrappedResearchDraftV1(pages, List.of(source)),
                List.of(source.url()), "stubbed-unwrapped", "stub-post-" + request.postId());
    }

    private String researchPrompt(UnwrappedResearchRequest request) throws Exception {
        List<Long> optionIds = request.options().stream()
                .map(option -> option.option().id())
                .toList();
        return """
                OUTPUT CONTRACT:
                - Return exactly %d pages.
                - Return pages in this exact optionId order: %s.
                - Include every optionId exactly once; do not merge or omit options.
                - Use only cohort IDs supplied under that option.
                - Describe cohort patterns only as associations or cautious interpretations.
                - Do not use these causal words anywhere: because, cause, caused, drove, led, made, chose.
                - Never say that a cohort or demographic voted, supported, or opposed due to a characteristic.
                - You must call web search before drafting any page.
                - Include one to three contextClaims on every page; empty contextClaims are forbidden.
                - Give every contextClaim one or more sourceIds; empty sourceIds are forbidden.
                - Include every referenced source exactly once in sources; empty sources are forbidden.
                - Copy each source URL exactly from a URL returned by web search in this same call.
                - Do not include a source unless it directly supports at least one contextClaim.
                - Every caveat must include exactly: This association describes only people who voted on this post and does not represent any broader population.

                INPUT JSON:
                %s
                """.formatted(optionIds.size(), optionIds,
                objectMapper.writeValueAsString(request));
    }

    private List<String> citations(ChatResponse response) {
        if (!(response.metadata() instanceof OpenAiResponsesChatResponseMetadata metadata)
                || metadata.rawHttpResponse() == null || metadata.rawHttpResponse().body() == null) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_CITATIONS_MISSING");
        }
        try {
            JsonNode root = objectMapper.readTree(metadata.rawHttpResponse().body());
            List<String> values = new ArrayList<>();
            JsonNode array = root.path("citations");
            if (array.isArray()) {
                array.forEach(item -> {
                    if (item.isTextual()) values.add(item.asText());
                });
            }
            root.findValues("annotations").stream()
                    .filter(JsonNode::isArray)
                    .forEach(annotations -> annotations.forEach(annotation -> {
                        JsonNode url = annotation.path("url");
                        if (url.isTextual()) values.add(url.asText());
                    }));
            return values.stream()
                    .filter(value -> value.startsWith("https://"))
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_CITATIONS_INVALID", e);
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

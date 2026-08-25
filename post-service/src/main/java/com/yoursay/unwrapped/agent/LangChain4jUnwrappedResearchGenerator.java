package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.platform.ai.AiConfig;
import com.yoursay.platform.ai.AiFailureResponseLog;
import com.yoursay.platform.ai.AiProviderFailureLog;
import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.validation.UnwrappedDraftValidator;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** LangChain4j implementation kept behind the provider-neutral Unwrapped domain interface. */
@ApplicationScoped
@ActivateRequestContext
public class LangChain4jUnwrappedResearchGenerator implements UnwrappedResearchGenerator {
    @Inject
    UnwrappedResearchAiService aiService;

    @Inject
    UnwrappedChatResponseCapture responseCapture;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    UnwrappedDraftValidator validator;

    @Inject
    DomainMetrics metrics;

    @Inject
    AiConfig aiConfig;

    @Inject
    AiProviderFailureLog providerFailureLog;

    @Inject
    AiFailureResponseLog failureResponseLog;

    @Override
    public UnwrappedResearchResult generate(UnwrappedResearchRequest request) {
        return generate(request, null, true);
    }

    @Override
    public UnwrappedResearchResult generate(UnwrappedResearchRequest request, String systemPrompt) {
        return generate(request, systemPrompt, false);
    }

    private UnwrappedResearchResult generate(
            UnwrappedResearchRequest request,
            String systemPrompt,
            boolean enforcePublicationContract
    ) {
        if (aiConfig.unwrapped().stubbed()) return stubbedResult(request);
        long started = System.nanoTime();
        try {
            UnwrappedResearchResult result = generateWithProvider(
                    request, systemPrompt, enforcePublicationContract);
            metrics.recordOperation("unwrapped", "research_provider",
                    "success", "none", "none", System.nanoTime() - started);
            return result;
        } catch (RuntimeException failure) {
            recordFailure(failure, started);
            throw failure;
        }
    }

    private UnwrappedResearchResult generateWithProvider(
            UnwrappedResearchRequest request,
            String systemPrompt,
            boolean enforcePublicationContract
    ) {
        requireProviderConfigured();
        responseCapture.begin();
        ChatResponse response = null;
        try {
            UnwrappedResearchDraftV1 draft = requestDraft(request, systemPrompt);
            // The capture holds the raw provider response, which carries the citations and the
            // model actually used — neither is available on the deserialised draft.
            response = responseCapture.take();
            if (enforcePublicationContract && response == null) {
                throw new IllegalStateException("UNWRAPPED_PROVIDER_RESPONSE_MISSING");
            }
            requireDraftContent(draft, response);
            List<String> citations = enforcePublicationContract ? citations(response) : List.of();
            if (enforcePublicationContract) validator.validate(request, draft, citations);
            logDraftReceived(draft, citations);
            return new UnwrappedResearchResult(draft, citations,
                    response == null
                            ? aiConfig.unwrapped().model()
                            : valueOr(response.modelName(), aiConfig.unwrapped().model()),
                    response == null ? null : response.id());
        } catch (IllegalStateException | IllegalArgumentException e) {
            logFailureResponse(e, response);
            throw e;
        } catch (Exception e) {
            IllegalStateException failure =
                    new IllegalStateException("UNWRAPPED_PROVIDER_FAILURE", e);
            logFailureResponse(failure, response);
            throw failure;
        } finally {
            responseCapture.clear();
        }
    }

    private void logFailureResponse(RuntimeException failure, ChatResponse response) {
        ChatResponse failedResponse = response == null ? responseCapture.take() : response;
        failureResponseLog.log("unwrapped", "research_provider",
                stableErrorCode(failure), failedResponse);
    }

    private void recordFailure(RuntimeException failure, long started) {
        String errorCode = stableErrorCode(failure);
        String outcome = failureOutcome(errorCode);
        String errorType = failureType(errorCode, failure);
        metrics.recordOperation("unwrapped", "research_provider", outcome, errorType,
                errorCode, System.nanoTime() - started);
        providerFailureLog.logNonSuccessResponse(aiConfig.provider(), "unwrapped",
                "research_provider", errorCode, failure);
        Log.warn(failureLogMessage(outcome, errorType, errorCode));
    }

    static String failureLogMessage(String outcome, String errorType, String errorCode) {
        return "Unwrapped research failed: domain=unwrapped operation=research_provider "
                + "outcome=" + outcome + " errorType=" + errorType + " errorCode=" + errorCode;
    }

    private static String failureOutcome(String errorCode) {
        return "UNWRAPPED_PROVIDER_NOT_CONFIGURED".equals(errorCode)
                ? "service_error"
                : "dependency_error";
    }

    private static String failureType(String errorCode, RuntimeException failure) {
        if ("UNWRAPPED_PROVIDER_NOT_CONFIGURED".equals(errorCode)) return "configuration";
        return failure instanceof IllegalArgumentException || providerContractFailure(errorCode)
                ? "provider_contract"
                : "provider";
    }

    private static boolean providerContractFailure(String errorCode) {
        return "UNWRAPPED_PROVIDER_RESPONSE_MISSING".equals(errorCode)
                || errorCode.startsWith("UNWRAPPED_PROVIDER_CITATIONS_");
    }

    private static String stableErrorCode(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || !message.startsWith("UNWRAPPED_")) {
            return "UNWRAPPED_PROVIDER_FAILURE";
        }
        int separator = message.indexOf(':');
        return separator < 0 ? message : message.substring(0, separator);
    }

    private void requireProviderConfigured() {
        if (!aiConfig.unwrapped().configured()) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_NOT_CONFIGURED");
        }
    }

    /** Requires the structured response container, without validating model-written content. */
    private static void requireDraftContent(
            UnwrappedResearchDraftV1 draft,
            ChatResponse response
    ) {
        if (draft == null || draft.pages() == null || draft.pages().isEmpty()
                || draft.pages().stream().allMatch(page -> page == null)) {
            Log.warn(missingDraftLogMessage(response, draft));
            throw new IllegalArgumentException("UNWRAPPED_DRAFT_MISSING");
        }
    }

    static String missingDraftLogMessage(
            ChatResponse response,
            UnwrappedResearchDraftV1 draft
    ) {
        TokenUsage usage = response == null ? null : response.tokenUsage();
        String responseText = response == null ? null : response.aiMessage().text();
        int pageCount = draft == null || draft.pages() == null ? 0 : draft.pages().size();
        long nonNullPageCount = draft == null || draft.pages() == null
                ? 0
                : draft.pages().stream().filter(page -> page != null).count();
        return "Unwrapped provider returned no draft content: domain=unwrapped "
                + "operation=research_provider outcome=dependency_error "
                + "errorType=provider_contract errorCode=UNWRAPPED_DRAFT_MISSING "
                + "providerResponseCaptured=" + (response != null)
                + " model=" + safeModelName(response == null ? null : response.modelName())
                + " finishReason=" + metadataValue(response == null ? null : response.finishReason())
                + " inputTokens=" + metadataValue(usage == null ? null : usage.inputTokenCount())
                + " outputTokens=" + metadataValue(usage == null ? null : usage.outputTokenCount())
                + " totalTokens=" + metadataValue(usage == null ? null : usage.totalTokenCount())
                + " responseTextChars=" + (responseText == null ? 0 : responseText.length())
                + " toolRequests=" + (response == null
                        ? 0
                        : response.aiMessage().toolExecutionRequests().size())
                + " pages=" + pageCount
                + " nonNullPages=" + nonNullPageCount;
    }

    private static String metadataValue(Object value) {
        return value == null || value.toString().isBlank() ? "unknown" : value.toString();
    }

    private static String safeModelName(String modelName) {
        if (modelName == null || !modelName.matches("[A-Za-z0-9._:-]{1,80}")) return "unknown";
        return modelName;
    }

    /** Benchmarking supplies its own editorial prompt; queued generation uses the default one. */
    private UnwrappedResearchDraftV1 requestDraft(UnwrappedResearchRequest request, String systemPrompt)
            throws Exception {
        List<Long> optionIds = optionIds(request);
        String editorialPrompt = systemPrompt == null ? UnwrappedSystemPrompt.DEFAULT : systemPrompt;
        String outputInstructions = UnwrappedSystemPrompt.outputInstructions(optionIds);
        String input = inputPrompt(request);
        try {
            return aiService.research(editorialPrompt, outputInstructions, input);
        } catch (Exception failure) {
            throw new IllegalStateException(providerErrorCode(failure), failure);
        }
    }

    private static String providerErrorCode(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException) return "UNWRAPPED_PROVIDER_TIMEOUT";
            if (cause instanceof RateLimitException) return "UNWRAPPED_PROVIDER_RATE_LIMITED";
            if (cause instanceof AuthenticationException) {
                return "UNWRAPPED_PROVIDER_AUTHENTICATION";
            }
            if (cause instanceof ContentFilteredException) {
                return "UNWRAPPED_PROVIDER_CONTENT_FILTERED";
            }
            if (cause instanceof InvalidRequestException) {
                return "UNWRAPPED_PROVIDER_REQUEST_INVALID";
            }
            if (cause instanceof RetriableException) return "UNWRAPPED_PROVIDER_UNAVAILABLE";
        }
        return "UNWRAPPED_PROVIDER_FAILURE";
    }

    /** Pins down whether the model answered for every option it was asked about. */
    private static void logDraftReceived(
            UnwrappedResearchDraftV1 draft,
            List<String> citations
    ) {
        Log.infof("Unwrapped research completed: domain=unwrapped operation=research_provider "
                        + "outcome=success pages=%d citations=%d",
                draft.pages() == null ? 0 : draft.pages().size(), citations.size());
    }

    private static List<Long> optionIds(UnwrappedResearchRequest request) {
        return request.options().stream().map(option -> option.option().id()).toList();
    }

    /**
     * Deterministic stand-in used in development and integration tests so the whole generation path
     * runs without calling an external model.
     */
    private UnwrappedResearchResult stubbedResult(UnwrappedResearchRequest request) {
        UnwrappedSourceDraftV1 source = new UnwrappedSourceDraftV1(
                "stub-source", "https://www.ons.gov.uk/", "Office for National Statistics",
                "Deterministic Unwrapped integration-test source", SourceClassification.OFFICIAL);
        List<UnwrappedArgumentDraftV1> pages = request.options().stream()
                .map(option -> stubbedArgument(option, source))
                .toList();
        return new UnwrappedResearchResult(
                new UnwrappedResearchDraftV1(pages, List.of(source)),
                List.of(source.url()), "stubbed-unwrapped", "stub-post-" + request.postId());
    }

    private static UnwrappedArgumentDraftV1 stubbedArgument(OptionBriefV1 option,
                                                            UnwrappedSourceDraftV1 source) {
        boolean hasCohort = !option.candidates().isEmpty();
        String audience = hasCohort
                ? option.candidates().getFirst().displayName()
                : "People choosing " + option.option().label();
        return new UnwrappedArgumentDraftV1(
                option.option().id(),
                "Why " + audience.toLowerCase(Locale.ROOT) + " favour the development option",
                hasCohort
                        ? option.candidates().stream().limit(2)
                                .map(candidate -> candidate.cohortId()).toList()
                        : List.of(),
                List.of(
                        new UnwrappedArticleParagraphDraftV2(
                                audience + " are likely to favour this option because its immediate effects "
                                        + "fit the pressures represented by this deterministic development fixture. "
                                        + "The observed pattern is carried into the preview without inventing another audience.",
                                List.of(source.id())),
                        new UnwrappedArticleParagraphDraftV2(
                                "The fixed source provides stable context while the preview records "
                                        + option.overallVoteCount() + " votes, or "
                                        + option.overallVotePercentage() + " percent, for this option. "
                                        + "This keeps integration tests repeatable without calling an external model.",
                                List.of(source.id()))),
                "This analysis describes patterns among people who voted on this post; "
                        + "it cannot know every individual's reason.");
    }

    private String inputPrompt(UnwrappedResearchRequest request) throws Exception {
        return """
                INPUT JSON:
                %s
                """.formatted(objectMapper.writeValueAsString(request));
    }

    private List<String> citations(ChatResponse response) {
        OpenAiResponsesChatResponseMetadata metadata = citationMetadata(response);
        List<String> values;
        try {
            JsonNode root = objectMapper.readTree(metadata.rawHttpResponse().body());
            values = extractCitationValues(root);
        } catch (Exception e) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_CITATIONS_INVALID", e);
        }
        List<String> citations = values.stream()
                .filter(value -> value.regionMatches(true, 0, "https://", 0, 8))
                .distinct()
                .toList();
        if (citations.isEmpty()) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_CITATIONS_MISSING");
        }
        return citations;
    }

    private static OpenAiResponsesChatResponseMetadata citationMetadata(ChatResponse response) {
        if (!(response.metadata() instanceof OpenAiResponsesChatResponseMetadata metadata)
                || metadata.rawHttpResponse() == null || metadata.rawHttpResponse().body() == null) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_CITATIONS_MISSING");
        }
        return metadata;
    }

    private static List<String> extractCitationValues(JsonNode root) {
        List<String> values = new ArrayList<>();
        JsonNode topLevelCitations = root.path("citations");
        if (topLevelCitations.isArray()) {
            topLevelCitations.forEach(item -> {
                if (item.isTextual()) values.add(item.asText());
            });
        }
        JsonNode output = root.path("output");
        if (!output.isArray()) return values;
        output.forEach(item -> {
            if ("message".equals(item.path("type").asText())) {
                addMessageAnnotationUrls(item, values);
            }
            if ("web_search_call".equals(item.path("type").asText())
                    && "completed".equals(item.path("status").asText())) {
                addWebSearchActionUrls(item, values);
            }
        });
        return values;
    }

    private static void addMessageAnnotationUrls(JsonNode message, List<String> values) {
        JsonNode content = message.path("content");
        if (!content.isArray()) return;
        content.forEach(part -> {
            JsonNode annotations = part.path("annotations");
            if (!annotations.isArray()) return;
            annotations.forEach(annotation -> {
                    JsonNode url = annotation.path("url");
                    if ("url_citation".equals(annotation.path("type").asText())
                            && url.isTextual()) {
                        values.add(url.asText());
                    }
            });
        });
    }

    private static void addWebSearchActionUrls(JsonNode searchCall, List<String> values) {
        JsonNode sources = searchCall.path("action").path("sources");
        if (!sources.isArray()) return;
        sources.forEach(source -> {
            JsonNode url = source.path("url");
            if (url.isTextual()) values.add(url.asText());
        });
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

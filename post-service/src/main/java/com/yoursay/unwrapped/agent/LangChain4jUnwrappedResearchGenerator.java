package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.validation.UnwrappedDraftValidator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;

/** LangChain4j implementation kept behind the provider-neutral Unwrapped domain interface. */
@ApplicationScoped
@ActivateRequestContext
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
        return generate(request, null);
    }

    @Override
    public UnwrappedResearchResult generate(UnwrappedResearchRequest request, String systemPrompt) {
        if (stubbed) return stubbedResult(request);
        if (apiKey == null || apiKey.isBlank() || NOT_CONFIGURED.equals(apiKey)) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_NOT_CONFIGURED");
        }
        responseCapture.begin();
        try {
            String brief = researchPrompt(request);
            UnwrappedResearchDraftV1 draft = systemPrompt == null
                    ? aiService.research(brief)
                    : aiService.researchWithSystemPrompt(systemPrompt, brief);
            ChatResponse response = responseCapture.take();
            if (response == null) {
                throw new IllegalStateException("UNWRAPPED_PROVIDER_RESPONSE_MISSING");
            }
            if (draft == null) throw new IllegalArgumentException("UNWRAPPED_DRAFT_MISSING");
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
                        + ",cohorts=" + size(page.selectedCohortIds())
                        + ",paragraphs=" + size(page.paragraphs()))
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
                .map(option -> {
                    boolean hasCohort = !option.candidates().isEmpty();
                    String audience = hasCohort
                            ? option.candidates().getFirst().displayName()
                            : "People choosing " + option.option().label();
                    return new UnwrappedArgumentDraftV1(
                        option.option().id(), "Why " + audience.toLowerCase(java.util.Locale.ROOT)
                                + " favour the development option",
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
                })
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
                - When an option supplies cohort candidates, select one or two of their IDs and name a
                  selected cohort in the headline using its supplied displayName.
                - When an option supplies no cohort candidates, return an empty selectedCohortIds list,
                  write the strongest general researched case for that option, and do not invent a cohort.
                - Headlines must be catchy, 6 to 10 words, and must not use agreement or disagreement.
                - Write two or three paragraphs totalling 50 to 100 words for every page.
                - In those paragraphs, explain why the selected cohort, or voters choosing the option
                  when no cohort is supplied, are likely to favour that option.
                - Direct explanations using words such as because, led or drove are allowed.
                - Do not claim direct knowledge of every individual voter's private motivation.
                - You must call web search before drafting any page.
                - Give every paragraph one or more sourceIds; empty sourceIds are forbidden.
                - Include every referenced source exactly once in sources; empty sources are forbidden.
                - Copy each source URL exactly from a URL returned by web search in this same call.
                - Do not include a source unless it directly supports context used in a paragraph.
                - Every caveat must be exactly: This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.

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

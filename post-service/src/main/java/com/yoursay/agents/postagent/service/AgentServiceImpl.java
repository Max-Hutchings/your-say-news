package com.yoursay.agents.postagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.observability.DomainMetrics;
import com.yoursay.posts.VotingType;
import com.yoursay.agents.postagent.AgentService;
import com.yoursay.agents.postagent.PepperDraftStatus;
import com.yoursay.agents.postagent.client.AgentUserClient;
import com.yoursay.agents.postagent.dto.AgentDraftDto;
import com.yoursay.agents.postagent.dto.AgentGenerationEventDto;
import com.yoursay.agents.postagent.dto.AgentPublicationDto;
import com.yoursay.agents.postagent.dto.AgentSourceDto;
import com.yoursay.agents.postagent.dto.GenerateAgentPostRequest;
import com.yoursay.agents.postagent.dto.PepperDraftDto;
import com.yoursay.agents.postagent.dto.PepperPostDraftDto;
import com.yoursay.agents.postagent.dto.SourcedClaimDto;
import com.yoursay.agents.postagent.dto.UpdatePepperDraftRequest;
import com.yoursay.agents.postagent.error.AgentApiException;
import com.yoursay.agents.postagent.generator.GenerationException;
import com.yoursay.agents.postagent.generator.GenerationResult;
import com.yoursay.agents.postagent.generator.PepperPostGenerator;
import com.yoursay.agents.postagent.model.PepperAiDraftPost;
import com.yoursay.agents.postagent.model.PepperAiDraftPostRepository;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class AgentServiceImpl implements AgentService {

    static final String SAFE_FAILURE = "Pepper AI is having trouble, please try again later.";

    @Inject
    PepperAiDraftPostRepository repository;

    @Inject
    AgentUserClient userClient;

    @Inject
    PepperPostGenerator generator;

    @Inject
    PepperGenerationRegistry streams;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Validator validator;

    @Inject
    DomainMetrics metrics;

    @Inject
    RequestContextController requestContext;

    @ConfigProperty(name = "pepper.replica-id", defaultValue = "local")
    String replicaId;

    @Override
    public Multi<AgentGenerationEventDto> start(
            String authorization, GenerateAgentPostRequest request) {
        Long userId = resolvePublisher(authorization).userId();
        PepperAiDraftPost draft = createDraft(userId, request.request().trim());
        AgentGenerationEventDto received = event(PepperDraftStatus.RECEIVED, draft, null, null);
        Multi<AgentGenerationEventDto> stream = streams.open(draft.getId(), received);
        Thread.ofVirtual()
                .name("pepper-generation")
                .start(() -> {
                    boolean activated = requestContext.activate();
                    try {
                        generate(draft.getId(), draft.getPrompt());
                    } finally {
                        if (activated) {
                            requestContext.deactivate();
                        }
                    }
                });
        return stream;
    }

    @Override
    public Multi<AgentGenerationEventDto> events(
            UUID draftId, String requestedReplicaId, String authorization) {
        Long userId = resolvePublisher(authorization).userId();
        PepperAiDraftPost draft = owned(draftId, userId);
        if (requestedReplicaId == null
                || !requestedReplicaId.equals(draft.getReplicaId())
                || !replicaId.equals(draft.getReplicaId())) {
            throw AgentApiException.replicaUnavailable();
        }
        Optional<Multi<AgentGenerationEventDto>> live = streams.subscribe(draftId);
        if (live.isPresent()) {
            return live.get();
        }
        if (draft.getStatus() == PepperDraftStatus.FINISHED) {
            return Multi.createFrom().item(event(
                    PepperDraftStatus.FINISHED, draft, content(draft), null));
        }
        if (draft.getStatus() == PepperDraftStatus.FAILED) {
            return Multi.createFrom().item(event(
                    PepperDraftStatus.FAILED, draft, null, SAFE_FAILURE));
        }
        throw AgentApiException.replicaUnavailable();
    }

    @Override
    public Optional<PepperDraftDto> latest(String authorization) {
        Long userId = resolvePublisher(authorization).userId();
        return QuarkusTransaction.requiringNew().call(() -> repository.latestUnpublished(userId))
                .map(this::toDto);
    }

    @Override
    public PepperDraftDto save(
            UUID draftId, UpdatePepperDraftRequest request, String authorization) {
        Long userId = resolvePublisher(authorization).userId();
        validateContent(request.content());
        return QuarkusTransaction.requiringNew().call(() -> {
            PepperAiDraftPost draft = repository.findOwnedForUpdate(draftId, userId)
                    .orElseThrow(AgentApiException::draftMissing);
            requireEditable(draft);
            if (draft.getVersion() != request.version()) {
                throw AgentApiException.versionConflict();
            }
            validateCitationSubset(request.content().citations(), generatedContent(draft).citations());
            draft.replaceContent(objectMapper.valueToTree(request.content()));
            repository.flush();
            return toDto(draft);
        });
    }

    @Override
    public AgentPublicationDto preparePublication(
            UUID draftId, List<AgentSourceDto> selectedSources, String authorization) {
        Long userId = resolvePublisher(authorization).userId();
        return QuarkusTransaction.requiringNew().call(() -> {
            PepperAiDraftPost draft = repository.findOwned(draftId, userId)
                    .orElseThrow(AgentApiException::draftMissing);
            requireEditable(draft);
            List<AgentSourceDto> requested = selectedSources == null ? List.of() : List.copyOf(selectedSources);
            validateCitationSubset(requested, content(draft).citations());
            return new AgentPublicationDto(draftId, requested);
        });
    }

    @Override
    public void markPublished(UUID draftId, Long postId) {
        QuarkusTransaction.requiringNew().run(() -> {
            PepperAiDraftPost draft = repository.findByIdOptional(draftId)
                    .orElseThrow(AgentApiException::draftMissing);
            draft.markPublished(postId);
        });
    }

    private PepperAiDraftPost createDraft(Long userId, String prompt) {
        return QuarkusTransaction.requiringNew().call(() -> {
            PepperAiDraftPost draft = new PepperAiDraftPost(userId, prompt, replicaId);
            repository.persist(draft);
            repository.flush();
            return draft;
        });
    }

    private void generate(UUID draftId, String prompt) {
        long started = System.nanoTime();
        try {
            PepperAiDraftPost generating = update(draftId, PepperAiDraftPost::markGenerating);
            streams.emit(draftId, event(PepperDraftStatus.GENERATING, generating, null, null));

            GenerationResult result = generator.generate(prompt);
            PepperPostDraftDto editable = toEditable(result.draft());
            validateContent(editable);
            PepperAiDraftPost finished = QuarkusTransaction.requiringNew().call(() -> {
                PepperAiDraftPost draft = required(draftId);
                draft.markFinished(objectMapper.valueToTree(editable),
                        result.model(), result.providerResponseId());
                repository.flush();
                return draft;
            });
            streams.emit(draftId, event(PepperDraftStatus.FINISHED, finished, editable, null));
            recordGeneration("success", "none", "none", started);
            Log.info("domain=postagent operation=generation outcome=success");
        } catch (GenerationException fault) {
            fail(draftId, fault.code(), "provider_fault", started);
        } catch (RuntimeException fault) {
            fail(draftId, "AGENT_GENERATION_FAULT", "server_fault", started);
        }
    }

    private void fail(UUID draftId, String code, String faultType, long started) {
        PepperAiDraftPost failed = QuarkusTransaction.requiringNew().call(() -> {
            PepperAiDraftPost draft = required(draftId);
            draft.markFailed(code, SAFE_FAILURE);
            repository.flush();
            return draft;
        });
        streams.emit(draftId, event(PepperDraftStatus.FAILED, failed, null, SAFE_FAILURE));
        recordGeneration("fault", faultType, code, started);
        if (metrics != null) {
            metrics.recordError("postagent", "generation", code, 500);
        }
        Log.warnf("domain=postagent operation=generation outcome=fault fault_code=%s", code);
    }

    private PepperAiDraftPost update(UUID id, java.util.function.Consumer<PepperAiDraftPost> change) {
        return QuarkusTransaction.requiringNew().call(() -> {
            PepperAiDraftPost draft = required(id);
            change.accept(draft);
            repository.flush();
            return draft;
        });
    }

    private PepperAiDraftPost required(UUID id) {
        return repository.findByIdOptional(id).orElseThrow(AgentApiException::draftMissing);
    }

    private PepperAiDraftPost owned(UUID id, Long userId) {
        return QuarkusTransaction.requiringNew().call(() -> repository.findOwned(id, userId)
                .orElseThrow(AgentApiException::draftMissing));
    }

    private AgentUserClient.UserAccess resolvePublisher(String authorization) {
        try {
            AgentUserClient.UserAccess user = userClient.getCurrentUserAccess(authorization);
            if (user == null || user.userId() == null) {
                throw AgentApiException.userMissing();
            }
            if (!user.isActiveOfficialPublisher()) {
                throw AgentApiException.publishingForbidden(user.userId());
            }
            return user;
        } catch (AgentApiException exception) {
            throw exception;
        } catch (WebApplicationException exception) {
            if (exception.getResponse().getStatus() == 404) {
                throw AgentApiException.userMissing();
            }
            throw AgentApiException.userLookupFailed(exception.getResponse().getStatus());
        }
    }

    private void requireEditable(PepperAiDraftPost draft) {
        if (draft.getStatus() != PepperDraftStatus.FINISHED || !Boolean.TRUE.equals(draft.getSuccess())) {
            throw AgentApiException.draftNotReady();
        }
    }

    private void validateContent(PepperPostDraftDto draft) {
        if (!validator.validate(draft).isEmpty()) {
            throw AgentApiException.draftInvalid();
        }
        if (draft.votingType() == VotingType.BINARY) {
            if (!List.of("Agree", "Disagree").equals(draft.voteOptions())) {
                throw AgentApiException.draftInvalid();
            }
            return;
        }
        List<String> labels = draft.voteOptions();
        if (labels.size() < 2 || labels.size() > 5) {
            throw AgentApiException.draftInvalid();
        }
        Set<String> unique = new HashSet<>();
        for (String label : labels) {
            if (!unique.add(label.trim().toLowerCase(java.util.Locale.ROOT))) {
                throw AgentApiException.draftInvalid();
            }
        }
    }

    private static void validateCitationSubset(
            List<AgentSourceDto> selected, List<AgentSourceDto> permitted) {
        if (!new HashSet<>(permitted).containsAll(selected)) {
            throw AgentApiException.citationInvalid();
        }
    }

    private PepperPostDraftDto toEditable(AgentDraftDto draft) {
        return new PepperPostDraftDto(
                joinClaims(draft.summaryClaims()),
                draft.supportQuestion(),
                joinClaims(draft.caseForClaims()),
                joinClaims(draft.caseAgainstClaims()),
                draft.votingType(),
                draft.voteOptions().stream().map(option -> option.label()).toList(),
                List.copyOf(draft.sources())
        );
    }

    private static String joinClaims(List<SourcedClaimDto> claims) {
        return claims.stream().map(SourcedClaimDto::text).map(String::trim)
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private PepperDraftDto toDto(PepperAiDraftPost draft) {
        return new PepperDraftDto(
                draft.getId(), draft.getPrompt(), draft.getReplicaId(), draft.getStatus(),
                draft.getSuccess(), content(draft), draft.getErrorMessage(),
                draft.getPublishedPostId(), draft.getVersion());
    }

    private PepperPostDraftDto generatedContent(PepperAiDraftPost draft) {
        return fromJson(draft.getGeneratedContent());
    }

    private PepperPostDraftDto content(PepperAiDraftPost draft) {
        return fromJson(draft.getContent());
    }

    private PepperPostDraftDto fromJson(JsonNode json) {
        if (json == null || json.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(json, PepperPostDraftDto.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Pepper draft is invalid", exception);
        }
    }

    private AgentGenerationEventDto event(
            PepperDraftStatus status, PepperAiDraftPost draft,
            PepperPostDraftDto result, String errorMessage) {
        return new AgentGenerationEventDto(
                status, draft.getId(), draft.getReplicaId(), result, errorMessage);
    }

    private void recordGeneration(
            String outcome, String errorType, String errorCode, long started) {
        if (metrics != null) {
            metrics.recordOperation("postagent", "generation", outcome,
                    errorType, errorCode, System.nanoTime() - started);
        }
    }
}

package com.yoursay.unwrapped.service;

import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.unwrapped.InsightSelectionService;
import com.yoursay.unwrapped.OptionBriefV1;
import com.yoursay.unwrapped.UnwrappedAnalysisBriefV1;
import com.yoursay.unwrapped.UnwrappedMode;
import com.yoursay.unwrapped.UnwrappedResearchGenerator;
import com.yoursay.unwrapped.UnwrappedResearchRequest;
import com.yoursay.unwrapped.UnwrappedResearchResult;
import com.yoursay.votes.PostAnalysisAggregateService;
import com.yoursay.votes.PostAnalysisAggregateV1;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class UnwrappedGenerationWorker {
    private static final String NOT_CONFIGURED = "__not_configured__";

    @Inject
    UnwrappedJobProcessor processor;
    @Inject
    PostVotingConfigurationService posts;
    @Inject
    PostAnalysisAggregateService aggregates;
    @Inject
    InsightSelectionService selector;
    @Inject
    UnwrappedResearchGenerator generator;
    @ConfigProperty(name = "unwrapped.agent.api-key", defaultValue = NOT_CONFIGURED)
    String apiKey;

    @Scheduled(identity = "unwrapped-generation-worker",
            every = "${unwrapped.jobs.poll-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    void processNext() {
        if (apiKey == null || apiKey.isBlank() || NOT_CONFIGURED.equals(apiKey)) {
            return;
        }
        Optional<UnwrappedJobProcessor.JobWork> claimed = processor.claimNext();
        if (claimed.isEmpty()) return;
        UnwrappedJobProcessor.JobWork job = claimed.get();
        try {
            UnwrappedResearchRequest request = request(job);
            UnwrappedResearchResult result = generator.generate(request);
            processor.complete(job.id(), result);
            Log.infof("Unwrapped draft ready: jobId=%s postId=%d mode=%s",
                    job.id(), job.postId(), job.mode());
        } catch (RuntimeException failure) {
            processor.fail(job.id(), failure);
            Log.warnf("Unwrapped generation failed: jobId=%s postId=%d mode=%s code=%s",
                    job.id(), job.postId(), job.mode(), failure.getMessage());
        }
    }

    private UnwrappedResearchRequest request(UnwrappedJobProcessor.JobWork job) {
        if (job.mode() == UnwrappedMode.OBSERVED) {
            PostAnalysisAggregateV1 aggregate = aggregates.capture(job.postId());
            processor.attachAggregate(job.id(), aggregate.canonicalVoteCount(),
                    aggregate.aggregateVersion(), aggregate);
            UnwrappedAnalysisBriefV1 brief = selector.select(aggregate);
            return new UnwrappedResearchRequest(UnwrappedMode.OBSERVED, brief.postId(),
                    brief.summary(), brief.question(), brief.jurisdiction(), brief.canonicalVoteCount(),
                    brief.aggregateVersion(), brief.options());
        }
        PostVotingConfigurationDto post = posts.findByPostId(job.postId())
                .orElseThrow(() -> new IllegalStateException("UNWRAPPED_POST_MISSING"));
        List<OptionBriefV1> options = post.options().stream()
                .map(option -> new OptionBriefV1(option, 0, 0.0, List.of(),
                        List.of("Which groups may favour '" + option.label()
                                + "', and what current official evidence explains the case?"),
                        List.of("Do not imply predicted groups have voted.",
                                "Do not claim a demographic characteristic causes a view."),
                        null))
                .toList();
        processor.attachAggregate(job.id(), 0, null, null);
        return new UnwrappedResearchRequest(UnwrappedMode.PREDICTION, post.postId(),
                post.summary(), post.question(), post.jurisdiction(), 0, null, options);
    }
}

package com.yoursay.autopost.service;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class AutoPostWorker {

    @Inject
    AutoPostRunProcessor processor;

    @Inject
    AutoPostDiscoveryJob discoveryJob;

    @Scheduled(identity = "auto-post-discovery-worker",
            every = "${autopost.jobs.poll-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    public void processNext() {
        Optional<AutoPostRunProcessor.DiscoveryWork> next = processor.claimNextDiscovery();
        next.ifPresent(discoveryJob::executeDiscovery);
    }
}

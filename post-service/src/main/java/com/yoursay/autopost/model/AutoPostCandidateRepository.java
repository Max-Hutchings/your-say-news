package com.yoursay.autopost.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AutoPostCandidateRepository implements PanacheRepositoryBase<AutoPostCandidate, UUID> {

    public List<AutoPostCandidate> listByRun(UUID runId) {
        return find("runId = ?1 order by rank", runId).list();
    }

    public Optional<AutoPostCandidate> findInRun(UUID candidateId, UUID runId) {
        return find("id = ?1 and runId = ?2", candidateId, runId).firstResultOptional();
    }
}

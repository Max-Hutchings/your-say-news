package com.yoursay.autopost.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AutoPostCandidateSourceRepository
        implements PanacheRepositoryBase<AutoPostCandidateSource, UUID> {

    public List<AutoPostCandidateSource> listByCandidates(List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        return find("candidateId in ?1 order by candidateId, ordinal", candidateIds).list();
    }

    public List<AutoPostCandidateSource> listByCandidate(UUID candidateId) {
        return find("candidateId = ?1 order by ordinal", candidateId).list();
    }
}

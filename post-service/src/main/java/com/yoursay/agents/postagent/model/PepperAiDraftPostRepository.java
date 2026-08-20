package com.yoursay.agents.postagent.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PepperAiDraftPostRepository implements PanacheRepositoryBase<PepperAiDraftPost, UUID> {

    public Optional<PepperAiDraftPost> findOwned(UUID id, Long userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public Optional<PepperAiDraftPost> findOwnedForUpdate(UUID id, Long userId) {
        return find("id = ?1 and userId = ?2", id, userId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public Optional<PepperAiDraftPost> latestUnpublished(Long userId) {
        return find("userId = ?1 and publishedPostId is null order by updatedAt desc, id desc", userId)
                .firstResultOptional();
    }
}

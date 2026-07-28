package com.yoursay.unwrapped.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UnwrappedStoryRepository implements PanacheRepositoryBase<UnwrappedStory, UUID> {
    public Optional<UnwrappedStory> newestApproved(Long postId, long currentCount) {
        return find("""
                postId = ?1
                and reviewStatus = ?2
                and milestone <= ?3
                order by milestone desc, reviewedAt desc nulls last
                """, postId, UnwrappedReviewStatus.APPROVED, currentCount)
                .firstResultOptional();
    }

    public List<UnwrappedStory> drafts() {
        return list("reviewStatus = ?1 order by generatedAt", UnwrappedReviewStatus.DRAFT);
    }
}

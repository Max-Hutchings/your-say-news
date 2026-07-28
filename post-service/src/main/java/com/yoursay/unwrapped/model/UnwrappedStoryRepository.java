package com.yoursay.unwrapped.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;

@ApplicationScoped
public class UnwrappedStoryRepository implements PanacheRepositoryBase<UnwrappedStory, UUID> {
    public Optional<UnwrappedStory> newestApproved(Long postId, long currentCount) {
        return list("postId = ?1 and reviewStatus = ?2",
                postId, UnwrappedReviewStatus.APPROVED).stream()
                .filter(story -> story.milestone <= currentCount)
                .max(Comparator
                        .comparingInt((UnwrappedStory story) -> story.milestone)
                        .thenComparing(UnwrappedStory::getReviewedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    public List<UnwrappedStory> drafts() {
        return list("reviewStatus = ?1 order by generatedAt", UnwrappedReviewStatus.DRAFT);
    }
}

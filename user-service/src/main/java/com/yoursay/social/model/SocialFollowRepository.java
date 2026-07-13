package com.yoursay.social.model;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional
public class SocialFollowRepository implements PanacheRepository<SocialFollow> {

    public SocialFollow findPair(long followerUserId, long followedUserId) {
        return find("followerUserId = ?1 and followedUserId = ?2", followerUserId, followedUserId)
                .firstResult();
    }

    public void save(SocialFollow follow) {
        follow.persist();
    }

    public void deletePair(long followerUserId, long followedUserId) {
        delete("followerUserId = ?1 and followedUserId = ?2", followerUserId, followedUserId);
    }

    public Set<Long> followingIds(long followerUserId) {
        return find("followerUserId", followerUserId).stream()
                .map(SocialFollow::getFollowedUserId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public long countFollowers(long userId) {
        return count("followedUserId", userId);
    }

    public long countFollowing(long userId) {
        return count("followerUserId", userId);
    }

    /** Ids of users who follow {@code userId}, most recent first, one page at a time. */
    public List<Long> followerIdsPage(long userId, int page, int size) {
        return find("followedUserId", newestFirst(), userId)
                .page(Page.of(page, size))
                .stream()
                .map(SocialFollow::getFollowerUserId)
                .toList();
    }

    /** Ids of users {@code userId} follows, most recent first, one page at a time. */
    public List<Long> followingIdsPage(long userId, int page, int size) {
        return find("followerUserId", newestFirst(), userId)
                .page(Page.of(page, size))
                .stream()
                .map(SocialFollow::getFollowedUserId)
                .toList();
    }

    // Deterministic order: newest follow first, id as a stable tie-break so paging never repeats
    // or skips a row when two follows share a created_at.
    private static Sort newestFirst() {
        return Sort.by("createdAt").descending().and("id").descending();
    }
}

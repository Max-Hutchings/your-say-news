package com.yoursay.social.model;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

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
}

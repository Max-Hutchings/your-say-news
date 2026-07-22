package com.yoursay.feed.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yoursay.user.social.SocialService;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class SocialClient {

    @Inject
    SocialService socialService;

    @Inject
    SecurityIdentity securityIdentity;

    public Uni<FollowingRef> getFollowing(String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        return Uni.createFrom().item(() -> new FollowingRef(socialService.getFollowingUserIds(email)))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FollowingRef(Set<Long> userIds) {
    }
}

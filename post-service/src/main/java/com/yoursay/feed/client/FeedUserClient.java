package com.yoursay.feed.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yoursay.user.user.YourSayUserDto;
import com.yoursay.user.user.YourSayUserService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FeedUserClient {

    @Inject
    YourSayUserService userService;

    public Uni<UserRef> getUserByEmail(String email, String authorization) {
        return Uni.createFrom().item(() -> {
                    YourSayUserDto user = userService.getByEmail(email);
                    return user == null ? null : new UserRef(user.id());
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRef(Long id) {
    }
}

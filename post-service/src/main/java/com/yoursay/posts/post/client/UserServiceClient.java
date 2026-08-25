package com.yoursay.posts.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yoursay.user.user.dto.UserAccessDto;
import com.yoursay.user.user.dto.YourSayUserDto;
import com.yoursay.user.user.YourSayUserService;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class UserServiceClient {

    @Inject
    YourSayUserService userService;

    @Inject
    SecurityIdentity securityIdentity;

    public Uni<Response> getUser(Long id) {
        return blocking(() -> {
            YourSayUserDto user = userService.getById(id);
            return user == null ? Response.noContent().build() : Response.ok(user).build();
        });
    }

    /**
     * Public handles for a page of authors, keyed by user id, so posts can be labelled with their
     * author's username in one lookup rather than one per card.
     */
    public Uni<Map<Long, String>> usernamesByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }
        return blocking(() -> userService.usernamesByIds(userIds));
    }

    /** Resolve the authenticated subject and publishing capability without accepting caller identity. */
    public Uni<UserAccess> getCurrentUserAccess(String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        return blocking(() -> toAccess(userService.getAccessByEmail(email)));
    }

    private static UserAccess toAccess(UserAccessDto access) {
        return access == null ? null : new UserAccess(
                access.userId(),
                access.accountType().name(),
                access.publisherStatus().name(),
                access.canPublish());
    }

    private static <T> Uni<T> blocking(java.util.function.Supplier<T> operation) {
        Context callerContext = Vertx.currentContext();
        Uni<T> result = Uni.createFrom().item(operation)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
        return callerContext == null
                ? result
                : result.emitOn(command -> callerContext.runOnContext(ignored -> command.run()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserAccess(Long userId, String accountType, String publisherStatus, boolean canPublish) {

        public boolean isActiveOfficialPublisher() {
            return "OFFICIAL".equals(accountType)
                    && "ACTIVE".equals(publisherStatus)
                    && canPublish;
        }
    }
}

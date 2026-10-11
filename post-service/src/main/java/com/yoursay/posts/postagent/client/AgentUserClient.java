package com.yoursay.posts.postagent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yoursay.user.user.dto.UserAccessDto;
import com.yoursay.user.user.YourSayUserService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AgentUserClient {

    @Inject
    YourSayUserService userService;

    @Inject
    SecurityIdentity securityIdentity;

    public UserAccess getCurrentUserAccess(String authorization) {
        UserAccessDto access = userService.getAccessByEmail(securityIdentity.getPrincipal().getName());
        return access == null ? null : new UserAccess(
                access.userId(),
                access.accountType().name(),
                access.canPublish());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserAccess(Long userId, String accountType, boolean canPublish) {

        /**
         * Publishing is a permission an admin grants (ADR-066). A USER account is refused even if
         * the flag says otherwise, so one contradictory value cannot open publishing.
         */
        public boolean isPermittedPublisher() {
            return canPublish && ("OFFICIAL".equals(accountType) || "ADMIN".equals(accountType));
        }
    }
}

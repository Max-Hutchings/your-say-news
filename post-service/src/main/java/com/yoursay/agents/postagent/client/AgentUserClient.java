package com.yoursay.agents.postagent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yoursay.user.user.UserAccessDto;
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
                access.publisherStatus().name(),
                access.canPublish());
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

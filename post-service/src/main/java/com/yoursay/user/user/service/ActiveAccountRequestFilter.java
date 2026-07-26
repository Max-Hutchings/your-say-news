package com.yoursay.user.user.service;

import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.error.UserApiException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Applies database deactivation immediately across authenticated API functionality.
 *
 * Unknown subjects are allowed through so the normal first-login endpoint can provision them.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class ActiveAccountRequestFilter implements ContainerRequestFilter {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    YourSayUserService userService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (securityIdentity.isAnonymous()) {
            return;
        }

        String email = securityIdentity.getPrincipal().getName();
        if (userService.isInactive(email)) {
            throw UserApiException.inactive(email);
        }
    }
}

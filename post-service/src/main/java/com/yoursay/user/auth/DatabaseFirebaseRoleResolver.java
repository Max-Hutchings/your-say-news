package com.yoursay.user.auth;

import com.yoursay.user.user.YourSayUserService;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@UnlessBuildProfile("test")
class DatabaseFirebaseRoleResolver implements FirebaseRoleResolver {

    private final YourSayUserService userService;

    @Inject
    DatabaseFirebaseRoleResolver(YourSayUserService userService) {
        this.userService = userService;
    }

    /**
     * Open sign-up (ADR-062): any verified Firebase account is a user, so its first request can
     * provision its row. Only an account an administrator has deactivated is refused.
     */
    @Override
    public boolean hasActiveUserAccess(String email) {
        return !userService.isInactive(email);
    }

    @Override
    public boolean hasActiveAdminAccess(String email) {
        return userService.hasActiveAdminAccess(email);
    }
}

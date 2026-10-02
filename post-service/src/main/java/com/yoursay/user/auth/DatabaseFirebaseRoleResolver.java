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

    @Override
    public boolean hasActiveUserAccess(String email) {
        return userService.getAccessByEmail(email) != null && !userService.isInactive(email);
    }

    @Override
    public boolean hasActiveAdminAccess(String email) {
        return userService.hasActiveAdminAccess(email);
    }
}

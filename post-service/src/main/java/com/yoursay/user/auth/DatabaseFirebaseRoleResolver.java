package com.yoursay.user.auth;

import com.yoursay.user.user.YourSayUserService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProfile("dev")
class DatabaseFirebaseRoleResolver implements FirebaseRoleResolver {

    private final YourSayUserService userService;

    @Inject
    DatabaseFirebaseRoleResolver(YourSayUserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean hasActiveAdminAccess(String email) {
        return userService.hasActiveAdminAccess(email);
    }
}

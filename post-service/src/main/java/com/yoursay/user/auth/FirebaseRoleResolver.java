package com.yoursay.user.auth;

public interface FirebaseRoleResolver {

    boolean hasActiveUserAccess(String email);

    boolean hasActiveAdminAccess(String email);
}

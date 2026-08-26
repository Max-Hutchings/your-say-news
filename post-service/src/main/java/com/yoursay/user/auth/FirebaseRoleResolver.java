package com.yoursay.user.auth;

public interface FirebaseRoleResolver {

    boolean hasActiveAdminAccess(String email);
}

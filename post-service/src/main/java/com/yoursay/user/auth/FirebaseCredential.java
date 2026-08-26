package com.yoursay.user.auth;

record FirebaseCredential(Type type, String value) {

    enum Type {
        BEARER,
        SESSION_COOKIE
    }
}

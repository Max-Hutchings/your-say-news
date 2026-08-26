package com.yoursay.user.auth;

import com.yoursay.user.auth.dto.AdminIdentityDto;

public record CreatedAdminSession(String cookieValue, AdminIdentityDto identity) {
}

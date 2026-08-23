package com.yoursay.autopost.service;

import com.yoursay.autopost.error.AutoPostApiException;
import com.yoursay.user.user.AccountType;
import com.yoursay.user.user.PublisherStatus;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.dto.UserAccessDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Resolves the two authorities used by the admin-managed publication workflow. */
@ApplicationScoped
public class AutoPostAccessPolicy {

    @Inject
    YourSayUserService userService;

    @ConfigProperty(name = "autopost.official-handle", defaultValue = "yoursay")
    String officialHandle;

    public UserAccessDto requireAdministrator(String email) {
        if (!userService.hasActiveAdminAccess(email)) {
            throw AutoPostApiException.adminAccessRequired();
        }
        UserAccessDto access = userService.getAccessByEmail(email);
        if (access == null) {
            throw AutoPostApiException.adminAccessRequired();
        }
        return access;
    }

    public UserAccessDto requireOfficialAccount() {
        UserAccessDto official = userService.getAccessByHandle(officialHandle);
        if (official == null || !official.canPublish()
                || official.accountType() != AccountType.OFFICIAL
                || official.publisherStatus() != PublisherStatus.ACTIVE) {
            throw AutoPostApiException.officialAccountUnavailable();
        }
        return official;
    }
}

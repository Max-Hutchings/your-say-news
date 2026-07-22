package com.yoursay.user.user;

import com.yoursay.user.user.UserAccessDto;
import com.yoursay.user.user.YourSayUserDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Public contract for the user domain. Controllers and other domains depend on this
 * interface and on {@link YourSayUserDto} only — never on the entity or repository.
 */
public interface YourSayUserService {

    /**
     * Returns the user for the given identity, creating one from the authentication
     * details on first sight.
     */
    YourSayUserDto getOrCreateFromIdentity(String email, String firstName, String lastName);

    YourSayUserDto save(String email, String firstName, String lastName, LocalDate birthDate);

    YourSayUserDto getById(long id);

    /** Users for the given ids, in the same order as {@code ids} (unknown ids are dropped). */
    List<YourSayUserDto> getByIds(List<Long> ids);

    YourSayUserDto getByEmail(String email);

    /** PII-free account classification and publishing capability for the authenticated subject. */
    UserAccessDto getAccessByEmail(String email);

    /**
     * Record explicit consent to the privacy promise for the authenticated user, stamping the time
     * and the policy version they agreed to.
     */
    YourSayUserDto recordConsent(String email, String privacyPolicyVersion);
}

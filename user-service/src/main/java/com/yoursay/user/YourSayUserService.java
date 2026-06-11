package com.yoursay.user;

import java.time.LocalDate;

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

    YourSayUserDto getByEmail(String email);
}

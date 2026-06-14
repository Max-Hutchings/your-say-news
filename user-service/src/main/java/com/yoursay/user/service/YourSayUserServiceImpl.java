package com.yoursay.user.service;

import com.yoursay.user.YourSayUserDto;
import com.yoursay.user.YourSayUserService;
import com.yoursay.user.model.YourSayUser;
import com.yoursay.user.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

import java.time.LocalDate;

@ApplicationScoped
public class YourSayUserServiceImpl implements YourSayUserService {

    @Inject
    YourSayUserRepository yourSayUserRepository;

    @Override
    public YourSayUserDto getOrCreateFromIdentity(String email, String firstName, String lastName) {
        if (firstName == null || lastName == null || email == null) {
            Log.errorf("Failed when saving user: Name {%s %s}  Email {%s}", firstName, lastName, email);
            throw new WebApplicationException("Could not collect user details from authentication");
        }

        YourSayUser user = yourSayUserRepository.findByEmail(email);
        if (user == null) {
            user = yourSayUserRepository.saveYourSayUser(new YourSayUser(email, firstName, lastName));
        }
        return toDto(user);
    }

    @Override
    public YourSayUserDto save(String email, String firstName, String lastName, LocalDate birthDate) {
        YourSayUser user = yourSayUserRepository.saveYourSayUser(new YourSayUser(email, birthDate, firstName, lastName));
        return toDto(user);
    }

    @Override
    public YourSayUserDto getById(long id) {
        return toDto(yourSayUserRepository.findYourSayUserById(id));
    }

    @Override
    public YourSayUserDto getByEmail(String email) {
        return toDto(yourSayUserRepository.findByEmail(email));
    }

    @Override
    @Transactional
    public YourSayUserDto recordConsent(String email, String privacyPolicyVersion) {
        YourSayUser user = yourSayUserRepository.findByEmail(email);
        if (user == null) {
            throw new NotFoundException("No user account exists for the authenticated subject");
        }
        user.recordConsent(privacyPolicyVersion);
        return toDto(user);
    }

    private static YourSayUserDto toDto(YourSayUser user) {
        if (user == null) {
            return null;
        }
        return new YourSayUserDto(
                user.getId(),
                user.getEmail(),
                user.getfirstName(),
                user.getlastName(),
                user.getDateOfBirth(),
                user.getCreatedDate(),
                user.isActive(),
                user.getConsentedAt(),
                user.getPrivacyPolicyVersion()
        );
    }
}

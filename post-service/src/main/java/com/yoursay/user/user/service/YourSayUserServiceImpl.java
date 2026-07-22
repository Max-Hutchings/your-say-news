package com.yoursay.user.user.service;

import com.yoursay.observability.DomainMetrics;
import com.yoursay.user.user.UserAccessDto;
import com.yoursay.user.user.YourSayUserDto;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.error.UserApiException;
import com.yoursay.user.user.model.YourSayUser;
import com.yoursay.user.user.model.YourSayUserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class YourSayUserServiceImpl implements YourSayUserService {

    @Inject
    YourSayUserRepository yourSayUserRepository;

    @Inject
    DomainMetrics metrics;

    @Override
    public YourSayUserDto getOrCreateFromIdentity(String email, String firstName, String lastName) {
        try {
            if (firstName == null || lastName == null || email == null) {
                throw UserApiException.missingIdentity(email, firstName, lastName);
            }

            YourSayUser user = yourSayUserRepository.findByEmail(email);
            if (user == null) {
                user = yourSayUserRepository.saveYourSayUser(new YourSayUser(email, firstName, lastName));
            }
            recordMetric("getOrCreateFromIdentity", true);
            return toDto(user);
        } catch (RuntimeException e) {
            recordMetric("getOrCreateFromIdentity", false);
            throw e;
        }
    }

    @Override
    public YourSayUserDto save(String email, String firstName, String lastName, LocalDate birthDate) {
        try {
            YourSayUser user = yourSayUserRepository.saveYourSayUser(new YourSayUser(email, birthDate, firstName, lastName));
            recordMetric("save", true);
            return toDto(user);
        } catch (RuntimeException e) {
            recordMetric("save", false);
            throw e;
        }
    }

    @Override
    public YourSayUserDto getById(long id) {
        return toDto(yourSayUserRepository.findYourSayUserById(id));
    }

    @Override
    public List<YourSayUserDto> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, YourSayUserDto> byId = yourSayUserRepository.findUsersByIds(ids).stream()
                .map(YourSayUserServiceImpl::toDto)
                .collect(Collectors.toMap(YourSayUserDto::id, dto -> dto));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public YourSayUserDto getByEmail(String email) {
        return toDto(yourSayUserRepository.findByEmail(email));
    }

    @Override
    public UserAccessDto getAccessByEmail(String email) {
        YourSayUser user = yourSayUserRepository.findByEmail(email);
        if (user == null) {
            return null;
        }
        return new UserAccessDto(
                user.getId(),
                user.getAccountType(),
                user.getPublisherStatus(),
                user.canPublish()
        );
    }

    @Override
    @Transactional
    public YourSayUserDto recordConsent(String email, String privacyPolicyVersion) {
        try {
            YourSayUser user = yourSayUserRepository.findByEmail(email);
            if (user == null) {
                throw UserApiException.notFoundForAuthenticatedSubject(email);
            }
            user.recordConsent(privacyPolicyVersion);
            recordMetric("recordConsent", true);
            return toDto(user);
        } catch (RuntimeException e) {
            recordMetric("recordConsent", false);
            throw e;
        }
    }

    private void recordMetric(String operation, boolean success) {
        if (metrics != null) {
            metrics.recordOperation("user", operation, success);
        }
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
                user.getDisplayName(),
                user.getHandle(),
                user.getAvatarUrl(),
                user.getDateOfBirth(),
                user.getCreatedDate(),
                user.isActive(),
                user.getAccountType(),
                user.getPublisherStatus(),
                user.canPublish(),
                user.getConsentedAt(),
                user.getPrivacyPolicyVersion()
        );
    }
}

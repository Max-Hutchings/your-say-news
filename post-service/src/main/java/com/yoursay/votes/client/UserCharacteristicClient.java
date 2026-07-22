package com.yoursay.votes.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yoursay.user.user.YourSayUserDto;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.usercharacteristic.UserCharacteristicDto;
import com.yoursay.user.usercharacteristic.UserCharacteristicService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/** Local adapter retaining the former REST-client contract for the votes domain. */
@ApplicationScoped
public class UserCharacteristicClient {

    @Inject
    YourSayUserService userService;

    @Inject
    UserCharacteristicService characteristicService;

    @Inject
    SecurityIdentity securityIdentity;

    public Response getUserByEmail(String email, String authorization) {
        YourSayUserDto user = userService.getByEmail(email);
        return user == null
                ? Response.noContent().build()
                : Response.ok(new UserRef(user.id())).build();
    }

    public Response getMyCharacteristics(String authorization) {
        YourSayUserDto user = userService.getByEmail(securityIdentity.getPrincipal().getName());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        UserCharacteristicDto characteristic = characteristicService.getByUserId(user.id());
        return characteristic == null
                ? Response.noContent().build()
                : Response.ok(toView(characteristic)).build();
    }

    private static UserCharacteristicView toView(UserCharacteristicDto dto) {
        return new UserCharacteristicView(
                dto.userId(),
                dto.politicalPersuasion(),
                dto.ageRange(),
                dto.gender(),
                dto.sexAtBirth(),
                dto.sexualOrientation(),
                dto.maritalStatus(),
                dto.race(),
                dto.country(),
                dto.region(),
                dto.urbanRural(),
                dto.ukCounty(),
                dto.countryOfBirth(),
                dto.citizenship(),
                dto.religion(),
                dto.religiosity(),
                dto.education(),
                dto.occupation(),
                dto.employmentSector(),
                dto.universitySubject(),
                dto.personalIncomeRange(),
                dto.householdIncomeRange(),
                dto.height(),
                dto.weightRange(),
                dto.eyeColor(),
                dto.parent(),
                dto.newsFrequency(),
                dto.hasPet(),
                dto.petType(),
                dto.chronotype(),
                dto.outlook(),
                dto.neurodivergent(),
                dto.neurodivergenceType(),
                dto.hasDisability(),
                dto.disabilityType(),
                dto.housingStatus(),
                dto.propertyType());
    }

    /** Minimal view of a user — only the id crosses into the votes domain. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRef(Long id) {
    }
}

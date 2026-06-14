package com.yoursay.usercharacteristic.service;

import com.yoursay.usercharacteristic.UserCharacteristicDto;
import com.yoursay.usercharacteristic.UserCharacteristicService;
import com.yoursay.usercharacteristic.model.Enums.*;
import com.yoursay.usercharacteristic.model.UserCharacteristic;
import com.yoursay.usercharacteristic.model.UserCharacteristicRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserCharacteristicServiceImpl implements UserCharacteristicService {

    @Inject
    UserCharacteristicRepository characteristicRepository;

    @Override
    public UserCharacteristicDto getByUserId(long userId) {
        return toDto(characteristicRepository.getUserCharacteristicByUserId(userId));
    }

    @Override
    @Transactional
    public UserCharacteristicDto saveForUser(long userId, UserCharacteristicDto answers) {
        UserCharacteristic entity = characteristicRepository.getUserCharacteristicByUserId(userId);
        if (entity == null) {
            entity = new UserCharacteristic();
            entity.setUserId(userId);
        }
        applyAnswers(entity, answers);
        return toDto(characteristicRepository.saveUserCharacteristic(entity));
    }

    /** Validates and copies the answer fields onto the entity. {@code userId} is never read from the body. */
    private static void applyAnswers(UserCharacteristic entity, UserCharacteristicDto a) {
        if (a.country() == null || a.country().isBlank()) {
            throw new BadRequestException("country is required");
        }
        entity.setCountry(a.country().trim());
        entity.setCity(blankToNull(a.city()));
        entity.setRegion(blankToNull(a.region()));
        entity.setUkCounty(parse(UKCounty.class, a.ukCounty()));
        entity.setUrbanRural(parse(UrbanRural.class, a.urbanRural()));

        entity.setAgeRange(required(AgeRange.class, a.ageRange(), "ageRange"));
        entity.setGender(required(Gender.class, a.gender(), "gender"));
        entity.setGenderSelfDescribe(blankToNull(a.genderSelfDescribe()));
        entity.setSexAtBirth(required(SexAtBirth.class, a.sexAtBirth(), "sexAtBirth"));
        entity.setSexualOrientation(parse(SexualOrientation.class, a.sexualOrientation()));
        entity.setMaritalStatus(parse(MaritalStatus.class, a.maritalStatus()));
        entity.setRaces(parseRaces(a.race()));

        entity.setCountryOfBirth(parse(CountryOfBirth.class, a.countryOfBirth()));
        entity.setCitizenship(parse(CountryOfBirth.class, a.citizenship()));
        entity.setReligion(parse(Religion.class, a.religion()));
        entity.setReligiosity(parse(Religiosity.class, a.religiosity()));
        entity.setPoliticalPersuasion(parse(PoliticalPersuasion.class, a.politicalPersuasion()));

        entity.setEducation(parse(EducationLevel.class, a.education()));
        entity.setOccupation(parse(OccupationStatus.class, a.occupation()));
        entity.setEmploymentSector(parse(EmploymentSector.class, a.employmentSector()));
        entity.setUniversitySubject(parse(UniversitySubject.class, a.universitySubject()));

        entity.setIncomeRange(required(IncomeRange.class, a.incomeRange(), "incomeRange"));
        entity.setHeight(required(Height.class, a.height(), "height"));
        entity.setWeightRange(required(WeightRange.class, a.weightRange(), "weightRange"));
        entity.setEyeColor(parse(EyeColor.class, a.eyeColor()));
        entity.setParent(parse(Parent.class, a.parent()));
        entity.setNewsFrequency(a.newsFrequency());
    }

    private static Set<Race> parseRaces(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BadRequestException("race must have at least one value");
        }
        Set<Race> races = new LinkedHashSet<>();
        for (String value : values) {
            races.add(required(Race.class, value, "race"));
        }
        return races;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Parse an optional enum value: {@code null}/blank stays {@code null}; an unknown value is a 400. */
    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value '" + value + "' for " + type.getSimpleName());
        }
    }

    /** Parse a required enum value: {@code null}/blank or an unknown value is a 400. */
    private static <E extends Enum<E>> E required(Class<E> type, String value, String field) {
        E parsed = parse(type, value);
        if (parsed == null) {
            throw new BadRequestException(field + " is required");
        }
        return parsed;
    }

    private static UserCharacteristicDto toDto(UserCharacteristic c) {
        if (c == null) {
            return null;
        }
        return new UserCharacteristicDto(
                c.getId(),
                c.getUserId(),
                c.getCountry(),
                c.getCity(),
                c.getRegion(),
                name(c.getUkCounty()),
                name(c.getUrbanRural()),
                name(c.getAgeRange()),
                name(c.getGender()),
                c.getGenderSelfDescribe(),
                name(c.getSexAtBirth()),
                name(c.getSexualOrientation()),
                name(c.getMaritalStatus()),
                c.getRaces().stream().map(Enum::name).collect(Collectors.toList()),
                name(c.getCountryOfBirth()),
                name(c.getCitizenship()),
                name(c.getReligion()),
                name(c.getReligiosity()),
                name(c.getPoliticalPersuasion()),
                name(c.getEducation()),
                name(c.getOccupation()),
                name(c.getEmploymentSector()),
                name(c.getUniversitySubject()),
                name(c.getIncomeRange()),
                name(c.getHeight()),
                name(c.getWeightRange()),
                name(c.getEyeColor()),
                name(c.getParent()),
                c.getNewsFrequency()
        );
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}

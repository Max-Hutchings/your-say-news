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
        entity.setUrbanRural(required(UrbanRural.class, a.urbanRural(), "urbanRural"));

        entity.setAgeRange(required(AgeRange.class, a.ageRange(), "ageRange"));
        entity.setGender(required(Gender.class, a.gender(), "gender"));
        entity.setGenderSelfDescribe(null);
        entity.setSexAtBirth(required(SexAtBirth.class, a.sexAtBirth(), "sexAtBirth"));
        entity.setSexualOrientation(required(SexualOrientation.class, a.sexualOrientation(), "sexualOrientation"));
        entity.setMaritalStatus(required(MaritalStatus.class, a.maritalStatus(), "maritalStatus"));
        entity.setRaces(parseRaces(a.race()));

        entity.setCountryOfBirth(required(CountryOfBirth.class, a.countryOfBirth(), "countryOfBirth"));
        entity.setCitizenship(required(Nationality.class, a.citizenship(), "citizenship"));
        entity.setReligion(required(Religion.class, a.religion(), "religion"));
        entity.setReligiosity(required(Religiosity.class, a.religiosity(), "religiosity"));
        entity.setPoliticalPersuasion(required(PoliticalPersuasion.class, a.politicalPersuasion(), "politicalPersuasion"));

        entity.setEducation(required(EducationLevel.class, a.education(), "education"));
        entity.setOccupation(required(OccupationStatus.class, a.occupation(), "occupation"));
        entity.setEmploymentSector(required(EmploymentSector.class, a.employmentSector(), "employmentSector"));
        entity.setUniversitySubject(parse(UniversitySubject.class, a.universitySubject()));

        entity.setPersonalIncomeRange(required(IncomeRange.class, a.personalIncomeRange(), "personalIncomeRange"));
        entity.setHouseholdIncomeRange(required(IncomeRange.class, a.householdIncomeRange(), "householdIncomeRange"));
        entity.setHeight(required(Height.class, a.height(), "height"));
        entity.setWeightRange(required(WeightRange.class, a.weightRange(), "weightRange"));
        entity.setEyeColor(required(EyeColor.class, a.eyeColor(), "eyeColor"));
        entity.setParent(required(Parent.class, a.parent(), "parent"));
        if (a.newsFrequency() == null) {
            throw new BadRequestException("newsFrequency is required");
        }
        entity.setNewsFrequency(a.newsFrequency());

        if (a.hasPet() == null) {
            throw new BadRequestException("hasPet is required");
        }
        entity.setHasPet(a.hasPet());
        // petType is only meaningful for pet owners; for non-owners it is forced to null.
        if (a.hasPet()) {
            entity.setPetType(required(PetType.class, a.petType(), "petType"));
        } else {
            entity.setPetType(null);
        }

        entity.setChronotype(required(Chronotype.class, a.chronotype(), "chronotype"));
        entity.setOutlook(required(Outlook.class, a.outlook(), "outlook"));

        if (a.neurodivergent() == null) {
            throw new BadRequestException("neurodivergent is required");
        }
        entity.setNeurodivergent(a.neurodivergent());
        // neurodivergenceType is only meaningful when neurodivergent; otherwise it is forced to null.
        if (a.neurodivergent()) {
            entity.setNeurodivergenceType(required(NeurodivergenceType.class, a.neurodivergenceType(), "neurodivergenceType"));
        } else {
            entity.setNeurodivergenceType(null);
        }

        if (a.hasDisability() == null) {
            throw new BadRequestException("hasDisability is required");
        }
        entity.setHasDisability(a.hasDisability());
        // disabilityType is only meaningful when the user has a disability; otherwise forced to null.
        if (a.hasDisability()) {
            entity.setDisabilityType(required(DisabilityType.class, a.disabilityType(), "disabilityType"));
        } else {
            entity.setDisabilityType(null);
        }

        HousingStatus housingStatus = required(HousingStatus.class, a.housingStatus(), "housingStatus");
        entity.setHousingStatus(housingStatus);
        // propertyType is only meaningful for owners; otherwise it is forced to null.
        if (housingStatus == HousingStatus.OWN) {
            entity.setPropertyType(required(PropertyType.class, a.propertyType(), "propertyType"));
        } else {
            entity.setPropertyType(null);
        }
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
                name(c.getPersonalIncomeRange()),
                name(c.getHouseholdIncomeRange()),
                name(c.getHeight()),
                name(c.getWeightRange()),
                name(c.getEyeColor()),
                name(c.getParent()),
                c.getNewsFrequency(),
                c.getHasPet(),
                name(c.getPetType()),
                name(c.getChronotype()),
                name(c.getOutlook()),
                c.getNeurodivergent(),
                name(c.getNeurodivergenceType()),
                c.getHasDisability(),
                name(c.getDisabilityType()),
                name(c.getHousingStatus()),
                name(c.getPropertyType())
        );
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}

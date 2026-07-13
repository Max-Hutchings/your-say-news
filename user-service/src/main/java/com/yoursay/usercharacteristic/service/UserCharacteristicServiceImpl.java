package com.yoursay.usercharacteristic.service;

import com.yoursay.observability.DomainMetrics;
import com.yoursay.usercharacteristic.UserCharacteristicDto;
import com.yoursay.usercharacteristic.UserCharacteristicService;
import com.yoursay.usercharacteristic.error.UserCharacteristicApiException;
import com.yoursay.usercharacteristic.model.Enums.*;
import com.yoursay.usercharacteristic.model.UserCharacteristic;
import com.yoursay.usercharacteristic.model.UserCharacteristicRepository;
import com.yoursay.usercharacteristic.model.UserCharacteristicRules;
import com.yoursay.usercharacteristic.model.EnumOptionPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Year;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserCharacteristicServiceImpl implements UserCharacteristicService {

    @Inject
    UserCharacteristicRepository characteristicRepository;

    @Inject
    DomainMetrics metrics;

    @Override
    public UserCharacteristicDto getByUserId(long userId) {
        return toDto(characteristicRepository.getUserCharacteristicByUserId(userId));
    }

    @Override
    @Transactional
    public UserCharacteristicDto saveForUser(long userId, UserCharacteristicDto answers) {
        try {
            UserCharacteristic entity = characteristicRepository.getUserCharacteristicByUserId(userId);
            if (entity == null) {
                entity = new UserCharacteristic();
                entity.setUserId(userId);
            }
            applyAnswers(entity, answers);
            UserCharacteristicDto dto = toDto(characteristicRepository.saveUserCharacteristic(entity));
            recordMetric("saveForUser", true);
            return dto;
        } catch (RuntimeException e) {
            recordMetric("saveForUser", false);
            throw e;
        }
    }

    /** Validates and copies the answer fields onto the entity. {@code userId} is never read from the body. */
    private static void applyAnswers(UserCharacteristic entity, UserCharacteristicDto a) {
        if (a == null) {
            throw UserCharacteristicApiException.requestBodyRequired();
        }
        if (a.country() == null || a.country().isBlank()) {
            throw UserCharacteristicApiException.requiredField("country");
        }
        entity.setCountry(a.country().trim());
        entity.setCity(blankToNull(a.city()));
        entity.setRegion(blankToNull(a.region()));
        entity.setUkCounty(parse(UKCounty.class, a.ukCounty()));
        entity.setUrbanRural(required(UrbanRural.class, a.urbanRural(), "urbanRural"));

        // Age is collected as a number; we store only the derived birth year (ADR-017).
        if (a.age() == null) {
            throw UserCharacteristicApiException.requiredField("age");
        }
        if (a.age() < UserCharacteristicRules.MINIMUM_AGE) {
            throw UserCharacteristicApiException.invalidField(
                    "age", "must be at least " + UserCharacteristicRules.MINIMUM_AGE);
        }
        entity.setBirthYear(Year.now().getValue() - a.age());

        Gender gender = required(Gender.class, a.gender(), "gender");
        entity.setGender(gender);
        // Free-text self-description is captured only when the user chose to self-describe.
        if (gender == Gender.SELF_DESCRIBE) {
            String selfDescribe = blankToNull(a.genderSelfDescribe());
            if (selfDescribe == null) {
                throw UserCharacteristicApiException.requiredField("genderSelfDescribe");
            }
            entity.setGenderSelfDescribe(selfDescribe);
        } else {
            entity.setGenderSelfDescribe(null);
        }

        entity.setSexAtBirth(required(SexAtBirth.class, a.sexAtBirth(), "sexAtBirth"));
        entity.setSexualOrientation(required(SexualOrientation.class, a.sexualOrientation(), "sexualOrientation"));
        entity.setMaritalStatus(required(MaritalStatus.class, a.maritalStatus(), "maritalStatus"));
        if (a.race() == null || a.race().isEmpty()) {
            throw UserCharacteristicApiException.emptyRace();
        }
        entity.setRaces(parseSet(Race.class, a.race(), "race"));

        entity.setCountryOfBirth(required(CountryOfBirth.class, a.countryOfBirth(), "countryOfBirth"));
        entity.setCitizenships(parseSet(Nationality.class, a.citizenship(), "citizenship"));
        entity.setReligion(required(Religion.class, a.religion(), "religion"));
        entity.setReligiosity(required(Religiosity.class, a.religiosity(), "religiosity"));
        entity.setPoliticalPersuasion(required(PoliticalPersuasion.class, a.politicalPersuasion(), "politicalPersuasion"));

        EducationLevel education = required(EducationLevel.class, a.education(), "education");
        entity.setEducation(education);
        entity.setOccupation(required(OccupationStatus.class, a.occupation(), "occupation"));
        entity.setEmploymentSector(required(EmploymentSector.class, a.employmentSector(), "employmentSector"));
        entity.setUniversitySubject(isHigherEducation(education)
                ? parse(UniversitySubject.class, a.universitySubject())
                : null);

        entity.setPersonalIncomeRange(required(IncomeRange.class, a.personalIncomeRange(), "personalIncomeRange"));
        entity.setHouseholdIncomeRange(required(IncomeRange.class, a.householdIncomeRange(), "householdIncomeRange"));
        entity.setHeight(required(Height.class, a.height(), "height"));
        entity.setWeightRange(required(WeightRange.class, a.weightRange(), "weightRange"));
        entity.setEyeColor(required(EyeColor.class, a.eyeColor(), "eyeColor"));
        entity.setParent(required(Parent.class, a.parent(), "parent"));

        if (a.newsFrequency() == null) {
            throw UserCharacteristicApiException.requiredField("newsFrequency");
        }
        requireRange(a.newsFrequency(), 0, 10, "newsFrequency");
        entity.setNewsFrequency(a.newsFrequency());

        if (a.hasPet() == null) {
            throw UserCharacteristicApiException.requiredField("hasPet");
        }
        entity.setHasPet(a.hasPet());
        // Pet types are only meaningful for pet owners; non-owners carry none.
        entity.setPetTypes(a.hasPet() ? parseSet(PetType.class, a.petType(), "petType") : new LinkedHashSet<>());

        entity.setChronotype(required(Chronotype.class, a.chronotype(), "chronotype"));
        entity.setOutlook(required(Outlook.class, a.outlook(), "outlook"));

        if (a.neurodivergent() == null) {
            throw UserCharacteristicApiException.requiredField("neurodivergent");
        }
        entity.setNeurodivergent(a.neurodivergent());
        entity.setNeurodivergenceTypes(a.neurodivergent()
                ? parseSet(NeurodivergenceType.class, a.neurodivergenceType(), "neurodivergenceType")
                : new LinkedHashSet<>());

        if (a.hasDisability() == null) {
            throw UserCharacteristicApiException.requiredField("hasDisability");
        }
        entity.setHasDisability(a.hasDisability());
        entity.setDisabilityTypes(a.hasDisability()
                ? parseSet(DisabilityType.class, a.disabilityType(), "disabilityType")
                : new LinkedHashSet<>());

        HousingStatus housingStatus = required(HousingStatus.class, a.housingStatus(), "housingStatus");
        entity.setHousingStatus(housingStatus);
        // Home type is asked of everyone with a fixed home; no-fixed-address users have no home type.
        if (housingStatus == HousingStatus.TEMPORARY_NO_FIXED) {
            entity.setPropertyType(null);
        } else {
            entity.setPropertyType(required(PropertyType.class, a.propertyType(), "propertyType"));
        }

        entity.setBalancedNewsViewpoint(requiredBoolean(a.balancedNewsViewpoint(), "balancedNewsViewpoint"));
        if (a.mainstreamNewsPercent() == null) {
            throw UserCharacteristicApiException.requiredField("mainstreamNewsPercent");
        }
        requireRange(a.mainstreamNewsPercent(), 0, 100, "mainstreamNewsPercent");
        entity.setMainstreamNewsPercent(a.mainstreamNewsPercent());
        entity.setBetterWorldWithData(requiredBoolean(a.betterWorldWithData(), "betterWorldWithData"));
    }

    /** Parse a required multi-select enum list: {@code null}/empty or any unknown value is a 400. */
    private static <E extends Enum<E>> Set<E> parseSet(Class<E> type, List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw UserCharacteristicApiException.emptyMultiSelect(field);
        }
        Set<E> parsed = new LinkedHashSet<>();
        for (String value : values) {
            parsed.add(required(type, value, field));
        }
        return parsed;
    }

    private static Boolean requiredBoolean(Boolean value, String field) {
        if (value == null) {
            throw UserCharacteristicApiException.requiredField(field);
        }
        return value;
    }

    private static void requireRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw UserCharacteristicApiException.invalidField(
                    field, "must be between " + minimum + " and " + maximum);
        }
    }

    private static boolean isHigherEducation(EducationLevel education) {
        return education == EducationLevel.HIGHER_EDUCATION_BELOW_DEGREE
                || education == EducationLevel.BACHELORS
                || education == EducationLevel.MASTERS
                || education == EducationLevel.DOCTORATE;
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
            E parsed = Enum.valueOf(type, value);
            if (!EnumOptionPolicy.isOffered(parsed)) {
                throw UserCharacteristicApiException.invalidEnumValue(type.getSimpleName(), value, type);
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw UserCharacteristicApiException.invalidEnumValue(type.getSimpleName(), value, type);
        }
    }

    /** Parse a required enum value: {@code null}/blank or an unknown value is a 400. */
    private static <E extends Enum<E>> E required(Class<E> type, String value, String field) {
        E parsed = parse(type, value);
        if (parsed == null) {
            throw UserCharacteristicApiException.requiredField(field);
        }
        return parsed;
    }

    private static UserCharacteristicDto toDto(UserCharacteristic c) {
        if (c == null) {
            return null;
        }
        Integer age = c.getBirthYear() == null ? null : Year.now().getValue() - c.getBirthYear();
        String ageRange = age == null ? null : AgeRange.fromAge(age).name();
        return new UserCharacteristicDto(
                c.getId(),
                c.getUserId(),
                c.getCountry(),
                c.getCity(),
                c.getRegion(),
                name(c.getUkCounty()),
                name(c.getUrbanRural()),
                age,
                ageRange,
                name(c.getGender()),
                c.getGenderSelfDescribe(),
                name(c.getSexAtBirth()),
                name(c.getSexualOrientation()),
                name(c.getMaritalStatus()),
                names(c.getRaces()),
                name(c.getCountryOfBirth()),
                names(c.getCitizenships()),
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
                c.getHasPet(),
                names(c.getPetTypes()),
                name(c.getChronotype()),
                name(c.getOutlook()),
                c.getNeurodivergent(),
                names(c.getNeurodivergenceTypes()),
                c.getHasDisability(),
                names(c.getDisabilityTypes()),
                name(c.getHousingStatus()),
                name(c.getPropertyType()),
                c.getNewsFrequency(),
                c.getBalancedNewsViewpoint(),
                c.getMainstreamNewsPercent(),
                c.getBetterWorldWithData()
        );
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static List<String> names(Set<? extends Enum<?>> values) {
        return values == null ? List.of() : values.stream().map(Enum::name).collect(Collectors.toList());
    }

    private void recordMetric(String operation, boolean success) {
        if (metrics != null) {
            metrics.recordOperation("usercharacteristic", operation, success);
        }
    }
}

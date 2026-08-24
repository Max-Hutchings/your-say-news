package com.yoursay.user.usercharacteristic.service;

import com.yoursay.user.usercharacteristic.dto.UserCharacteristicDto;

import com.yoursay.user.usercharacteristic.dto.IncomeProfileDto;

import com.yoursay.user.usercharacteristic.dto.IncomeAnswerDto;

import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.user.usercharacteristic.*;
import com.yoursay.user.usercharacteristic.error.UserCharacteristicApiException;
import com.yoursay.user.usercharacteristic.model.EnumOptionPolicy;
import com.yoursay.user.usercharacteristic.model.Enums.*;
import com.yoursay.user.usercharacteristic.model.UserCharacteristic;
import com.yoursay.user.usercharacteristic.model.UserCharacteristicRepository;
import com.yoursay.user.usercharacteristic.model.UserCharacteristicRules;
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

    @Inject
    IncomeProfileCatalog incomeProfiles;

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

    /**
     * Validates and copies the answer fields onto the entity, one group of characteristics at a
     * time. {@code userId} is never read from the body. The order is fixed because residence is
     * validated first and then reused when resolving the income profile.
     */
    private void applyAnswers(UserCharacteristic entity, UserCharacteristicDto answers) {
        if (answers == null) {
            throw UserCharacteristicApiException.requestBodyRequired();
        }
        CountryOfBirth residenceCountry = applyResidence(entity, answers);
        applyAge(entity, answers);
        applyGenderIdentity(entity, answers);
        applyRelationshipAndEthnicity(entity, answers);
        applyOriginAndBelief(entity, answers);
        applyEducationAndWork(entity, answers);
        applyIncome(entity, answers, residenceCountry);
        applyPhysicalTraits(entity, answers);
        applyHouseholdAndPets(entity, answers);
        applyDispositions(entity, answers);
        applyNeurodivergenceAndDisability(entity, answers);
        applyHousing(entity, answers);
        applyNewsAttitudes(entity, answers);
    }

    /** Returns the parsed residence country, which the income profile is later validated against. */
    private static CountryOfBirth applyResidence(UserCharacteristic entity, UserCharacteristicDto answers) {
        if (answers.country() == null || answers.country().isBlank()) {
            throw UserCharacteristicApiException.requiredField("country");
        }
        entity.setCountry(answers.country().trim());
        CountryOfBirth residenceCountry = parse(CountryOfBirth.class, answers.countryCode());
        entity.setCountryCode(name(residenceCountry));
        entity.setCity(blankToNull(answers.city()));
        entity.setRegion(blankToNull(answers.region()));
        entity.setUkCounty(parse(UKCounty.class, answers.ukCounty()));
        entity.setUrbanRural(required(UrbanRural.class, answers.urbanRural(), "urbanRural"));
        return residenceCountry;
    }

    /** Age is collected as a number; we store only the derived birth year (ADR-017). */
    private static void applyAge(UserCharacteristic entity, UserCharacteristicDto answers) {
        if (answers.age() == null) {
            throw UserCharacteristicApiException.requiredField("age");
        }
        if (answers.age() < UserCharacteristicRules.MINIMUM_AGE) {
            throw UserCharacteristicApiException.invalidField(
                    "age", "must be at least " + UserCharacteristicRules.MINIMUM_AGE);
        }
        entity.setBirthYear(Year.now().getValue() - answers.age());
    }

    private static void applyGenderIdentity(UserCharacteristic entity, UserCharacteristicDto answers) {
        Gender gender = required(Gender.class, answers.gender(), "gender");
        entity.setGender(gender);
        // Free-text self-description is captured only when the user chose to self-describe.
        if (gender != Gender.SELF_DESCRIBE) {
            entity.setGenderSelfDescribe(null);
            return;
        }
        String selfDescribe = blankToNull(answers.genderSelfDescribe());
        if (selfDescribe == null) {
            throw UserCharacteristicApiException.requiredField("genderSelfDescribe");
        }
        entity.setGenderSelfDescribe(selfDescribe);
    }

    private static void applyRelationshipAndEthnicity(UserCharacteristic entity, UserCharacteristicDto answers) {
        entity.setSexAtBirth(required(SexAtBirth.class, answers.sexAtBirth(), "sexAtBirth"));
        entity.setSexualOrientation(required(
                SexualOrientation.class, answers.sexualOrientation(), "sexualOrientation"));
        entity.setMaritalStatus(required(MaritalStatus.class, answers.maritalStatus(), "maritalStatus"));
        if (answers.race() == null || answers.race().isEmpty()) {
            throw UserCharacteristicApiException.emptyRace();
        }
        entity.setRaces(parseSet(Race.class, answers.race(), "race"));
    }

    private static void applyOriginAndBelief(UserCharacteristic entity, UserCharacteristicDto answers) {
        entity.setCountryOfBirth(required(CountryOfBirth.class, answers.countryOfBirth(), "countryOfBirth"));
        entity.setCitizenships(parseSet(Nationality.class, answers.citizenship(), "citizenship"));
        entity.setReligion(required(Religion.class, answers.religion(), "religion"));
        entity.setReligiosity(required(Religiosity.class, answers.religiosity(), "religiosity"));
        entity.setPoliticalPersuasion(required(
                PoliticalPersuasion.class, answers.politicalPersuasion(), "politicalPersuasion"));
    }

    private static void applyEducationAndWork(UserCharacteristic entity, UserCharacteristicDto answers) {
        EducationLevel education = required(EducationLevel.class, answers.education(), "education");
        entity.setEducation(education);
        entity.setOccupation(required(OccupationStatus.class, answers.occupation(), "occupation"));
        entity.setEmploymentSector(required(
                EmploymentSector.class, answers.employmentSector(), "employmentSector"));
        // A degree subject is only meaningful above school level, so it is dropped otherwise.
        entity.setUniversitySubject(isHigherEducation(education)
                ? parse(UniversitySubject.class, answers.universitySubject())
                : null);
    }

    /**
     * Version 2 answers name a currency-aware income profile, which must belong to the market the
     * user lives in. Version 1 answers are the legacy single-currency bands.
     */
    private void applyIncome(UserCharacteristic entity, UserCharacteristicDto answers,
                             CountryOfBirth residenceCountry) {
        if (answers.income() == null) {
            applyLegacyIncome(entity, answers);
            return;
        }
        if (answers.personalIncomeRange() != null || answers.householdIncomeRange() != null) {
            throw UserCharacteristicApiException.invalidField(
                    "income", "versioned and legacy income answers cannot be mixed");
        }
        if (residenceCountry == null) {
            throw UserCharacteristicApiException.requiredField("countryCode");
        }
        IncomeProfileCatalog.ResolvedIncomeAnswer resolved = incomeProfiles.resolve(answers.income());
        if (!incomeProfiles.isResidenceCompatible(residenceCountry.name(), resolved.profile())) {
            throw UserCharacteristicApiException.invalidField(
                    "income.profileId", "profile does not match the selected residence country");
        }
        applyVersionedIncome(entity, resolved);
    }

    private static void applyPhysicalTraits(UserCharacteristic entity, UserCharacteristicDto answers) {
        entity.setHeight(required(Height.class, answers.height(), "height"));
        entity.setWeightRange(required(WeightRange.class, answers.weightRange(), "weightRange"));
        entity.setEyeColor(required(EyeColor.class, answers.eyeColor(), "eyeColor"));
    }

    private static void applyHouseholdAndPets(UserCharacteristic entity, UserCharacteristicDto answers) {
        entity.setParent(required(Parent.class, answers.parent(), "parent"));
        if (answers.hasPet() == null) {
            throw UserCharacteristicApiException.requiredField("hasPet");
        }
        entity.setHasPet(answers.hasPet());
        // Pet types are only meaningful for pet owners; non-owners carry none.
        entity.setPetTypes(answers.hasPet()
                ? parseSet(PetType.class, answers.petType(), "petType")
                : new LinkedHashSet<>());
    }

    private static void applyDispositions(UserCharacteristic entity, UserCharacteristicDto answers) {
        entity.setChronotype(required(Chronotype.class, answers.chronotype(), "chronotype"));
        entity.setOutlook(required(Outlook.class, answers.outlook(), "outlook"));
    }

    /** Both answers gate a multi-select: the types are only stored when the user said yes. */
    private static void applyNeurodivergenceAndDisability(UserCharacteristic entity,
                                                          UserCharacteristicDto answers) {
        if (answers.neurodivergent() == null) {
            throw UserCharacteristicApiException.requiredField("neurodivergent");
        }
        entity.setNeurodivergent(answers.neurodivergent());
        entity.setNeurodivergenceTypes(answers.neurodivergent()
                ? parseSet(NeurodivergenceType.class, answers.neurodivergenceType(), "neurodivergenceType")
                : new LinkedHashSet<>());

        if (answers.hasDisability() == null) {
            throw UserCharacteristicApiException.requiredField("hasDisability");
        }
        entity.setHasDisability(answers.hasDisability());
        entity.setDisabilityTypes(answers.hasDisability()
                ? parseSet(DisabilityType.class, answers.disabilityType(), "disabilityType")
                : new LinkedHashSet<>());
    }

    private static void applyHousing(UserCharacteristic entity, UserCharacteristicDto answers) {
        HousingStatus housingStatus = required(HousingStatus.class, answers.housingStatus(), "housingStatus");
        entity.setHousingStatus(housingStatus);
        // Home type is asked of everyone with a fixed home; no-fixed-address users have no home type.
        entity.setPropertyType(housingStatus == HousingStatus.TEMPORARY_NO_FIXED
                ? null
                : required(PropertyType.class, answers.propertyType(), "propertyType"));
    }

    private static void applyNewsAttitudes(UserCharacteristic entity, UserCharacteristicDto answers) {
        if (answers.newsFrequency() == null) {
            throw UserCharacteristicApiException.requiredField("newsFrequency");
        }
        requireRange(answers.newsFrequency(), 0, 10, "newsFrequency");
        entity.setNewsFrequency(answers.newsFrequency());

        entity.setBalancedNewsViewpoint(requiredBoolean(
                answers.balancedNewsViewpoint(), "balancedNewsViewpoint"));
        if (answers.mainstreamNewsPercent() == null) {
            throw UserCharacteristicApiException.requiredField("mainstreamNewsPercent");
        }
        requireRange(answers.mainstreamNewsPercent(), 0, 100, "mainstreamNewsPercent");
        entity.setMainstreamNewsPercent(answers.mainstreamNewsPercent());
        entity.setBetterWorldWithData(requiredBoolean(
                answers.betterWorldWithData(), "betterWorldWithData"));
    }

    private static void applyVersionedIncome(
            UserCharacteristic entity,
            IncomeProfileCatalog.ResolvedIncomeAnswer resolved) {
        IncomeProfileDto profile = resolved.profile();
        entity.setPersonalIncomeRange(null);
        entity.setHouseholdIncomeRange(null);
        entity.setIncomeAnswerVersion(IncomeProfileCatalog.ANSWER_VERSION);
        entity.setIncomeCatalogVersion(profile.catalogVersion());
        entity.setIncomeProfileId(profile.profileId());
        entity.setIncomeProfileVersion(profile.profileVersion());
        entity.setIncomeCurrencyCode(profile.currencyCode());
        entity.setIncomeMarketCode(profile.marketCode());
        entity.setPersonalIncomeBandId(resolved.personalBand().id());
        entity.setHouseholdIncomeBandId(resolved.householdBand().id());
        entity.setPersonalIncomeTier(resolved.personalBand().tier());
        entity.setHouseholdIncomeTier(resolved.householdBand().tier());
        entity.setIncomeRangeProfileRefId(resolved.profileDatabaseId());
        entity.setPersonalIncomeBandRefId(resolved.personalBandDatabaseId());
        entity.setHouseholdIncomeBandRefId(resolved.householdBandDatabaseId());
    }

    private static void applyLegacyIncome(UserCharacteristic entity, UserCharacteristicDto answers) {
        entity.setPersonalIncomeRange(required(
                IncomeRange.class, answers.personalIncomeRange(), "personalIncomeRange"));
        entity.setHouseholdIncomeRange(required(
                IncomeRange.class, answers.householdIncomeRange(), "householdIncomeRange"));
        entity.setIncomeAnswerVersion(1);
        entity.setIncomeCatalogVersion(null);
        entity.setIncomeProfileId(null);
        entity.setIncomeProfileVersion(null);
        entity.setIncomeCurrencyCode(null);
        entity.setIncomeMarketCode(null);
        entity.setPersonalIncomeBandId(null);
        entity.setHouseholdIncomeBandId(null);
        entity.setPersonalIncomeTier(null);
        entity.setHouseholdIncomeTier(null);
        entity.setIncomeRangeProfileRefId(null);
        entity.setPersonalIncomeBandRefId(null);
        entity.setHouseholdIncomeBandRefId(null);
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
        IncomeAnswerDto income = c.getIncomeAnswerVersion() != null && c.getIncomeAnswerVersion() == 2
                ? new IncomeAnswerDto(
                        c.getIncomeAnswerVersion(),
                        c.getIncomeCatalogVersion(),
                        c.getIncomeProfileId(),
                        c.getIncomeProfileVersion(),
                        c.getIncomeMarketCode(),
                        c.getIncomeCurrencyCode(),
                        c.getPersonalIncomeBandId(),
                        c.getHouseholdIncomeBandId())
                : null;
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
                c.getBetterWorldWithData(),
                c.getCountryCode(),
                income,
                c.getPersonalIncomeTier(),
                c.getHouseholdIncomeTier()
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

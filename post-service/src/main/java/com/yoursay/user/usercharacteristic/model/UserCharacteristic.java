package com.yoursay.user.usercharacteristic.model;

import com.yoursay.user.usercharacteristic.model.Enums.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * A user's characteristic profile — the anonymised, aggregate-reportable data the product slices
 * sentiment by.
 *
 * <p><strong>PII boundary:</strong> the only link to identity is {@link #userId}, which is set
 * server-side from the authenticated subject — never trusted from the request body. No name, email
 * or exact DOB lives here; those stay in the {@code user} domain.
 */
@Entity
@Table(name = "user_characteristic")
public class UserCharacteristic extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // --- Location ---
    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "city")
    private String city;

    /** Country-agnostic region / state, free text (e.g. "California", "Bavaria"). */
    @Column(name = "region")
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "uk_county")
    private UKCounty ukCounty;

    @Enumerated(EnumType.STRING)
    @Column(name = "urban_rural")
    private UrbanRural urbanRural;

    // --- Who you are ---
    /** Birth year ({@code currentYear - age} at sign-up). Age and reporting band are derived on read (ADR-017). */
    @Column(name = "birth_year", nullable = false)
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "gender_self_describe")
    private String genderSelfDescribe;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex_at_birth", nullable = false)
    private SexAtBirth sexAtBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexual_orientation")
    private SexualOrientation sexualOrientation;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status")
    private MaritalStatus maritalStatus;

    /** Multi-select: a user can belong to more than one race/ethnicity bucket. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_characteristic_race",
            joinColumns = @JoinColumn(name = "user_characteristic_id"))
    @Column(name = "race", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Race> races = new HashSet<>();

    // --- Background ---
    @Enumerated(EnumType.STRING)
    @Column(name = "country_of_birth")
    private CountryOfBirth countryOfBirth;

    /** Nationality — multi-select; distinct from country of birth and country of residence. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_characteristic_citizenship",
            joinColumns = @JoinColumn(name = "user_characteristic_id"))
    @Column(name = "citizenship", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Nationality> citizenships = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "religion")
    private Religion religion;

    @Enumerated(EnumType.STRING)
    @Column(name = "religiosity")
    private Religiosity religiosity;

    @Enumerated(EnumType.STRING)
    @Column(name = "political_persuasion")
    private PoliticalPersuasion politicalPersuasion;

    // --- Education & work ---
    @Enumerated(EnumType.STRING)
    @Column(name = "education")
    private EducationLevel education;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupation")
    private OccupationStatus occupation;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_sector")
    private EmploymentSector employmentSector;

    @Enumerated(EnumType.STRING)
    @Column(name = "university_subject")
    private UniversitySubject universitySubject;

    // --- Finances & body ---
    @Enumerated(EnumType.STRING)
    @Column(name = "personal_income_range", nullable = false)
    private IncomeRange personalIncomeRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "household_income_range", nullable = false)
    private IncomeRange householdIncomeRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "height", nullable = false)
    private Height height;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_range", nullable = false)
    private WeightRange weightRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "eye_color")
    private EyeColor eyeColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "parent")
    private Parent parent;

    /** News-following frequency as a 0–10 self-report scale (see ScaleSelector). */
    @Column(name = "news_frequency")
    private Integer newsFrequency;

    // --- Lifestyle ---
    @Column(name = "has_pet")
    private Boolean hasPet;

    /** Kinds of pet — multi-select; empty unless {@link #hasPet} is true. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_characteristic_pet_type",
            joinColumns = @JoinColumn(name = "user_characteristic_id"))
    @Column(name = "pet_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<PetType> petTypes = new HashSet<>();

    /** Morning lark / night owl / in between. */
    @Enumerated(EnumType.STRING)
    @Column(name = "chronotype")
    private Chronotype chronotype;

    /** Optimist / pessimist / depends — disposition toward the future. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outlook")
    private Outlook outlook;

    // --- Neurodiversity & disability ---
    @Column(name = "neurodivergent")
    private Boolean neurodivergent;

    /** Kinds of neurodivergence — multi-select; empty unless {@link #neurodivergent} is true. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_characteristic_neurodivergence_type",
            joinColumns = @JoinColumn(name = "user_characteristic_id"))
    @Column(name = "neurodivergence_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<NeurodivergenceType> neurodivergenceTypes = new HashSet<>();

    @Column(name = "has_disability")
    private Boolean hasDisability;

    /** Kinds of disability — multi-select; empty unless {@link #hasDisability} is true. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_characteristic_disability_type",
            joinColumns = @JoinColumn(name = "user_characteristic_id"))
    @Column(name = "disability_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<DisabilityType> disabilityTypes = new HashSet<>();

    // --- Property ---
    @Enumerated(EnumType.STRING)
    @Column(name = "housing_status")
    private HousingStatus housingStatus;

    /** Type of home — asked of everyone with a current home; {@code null} only for no-fixed-address. */
    @Enumerated(EnumType.STRING)
    @Column(name = "property_type")
    private PropertyType propertyType;

    // --- News habits ---
    /** Whether the user regularly sees more than one viewpoint on the stories they follow. */
    @Column(name = "balanced_news_viewpoint")
    private Boolean balancedNewsViewpoint;

    /** Share of news from mainstream sources vs social media, 0–100 (the rest is social media). */
    @Column(name = "mainstream_news_percent")
    private Integer mainstreamNewsPercent;

    /** Belief that representative public-opinion data helps people understand society better. */
    @Column(name = "better_world_with_data")
    private Boolean betterWorldWithData;

    public UserCharacteristic() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public UKCounty getUkCounty() {
        return ukCounty;
    }

    public void setUkCounty(UKCounty ukCounty) {
        this.ukCounty = ukCounty;
    }

    public UrbanRural getUrbanRural() {
        return urbanRural;
    }

    public void setUrbanRural(UrbanRural urbanRural) {
        this.urbanRural = urbanRural;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getGenderSelfDescribe() {
        return genderSelfDescribe;
    }

    public void setGenderSelfDescribe(String genderSelfDescribe) {
        this.genderSelfDescribe = genderSelfDescribe;
    }

    public SexAtBirth getSexAtBirth() {
        return sexAtBirth;
    }

    public void setSexAtBirth(SexAtBirth sexAtBirth) {
        this.sexAtBirth = sexAtBirth;
    }

    public SexualOrientation getSexualOrientation() {
        return sexualOrientation;
    }

    public void setSexualOrientation(SexualOrientation sexualOrientation) {
        this.sexualOrientation = sexualOrientation;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public Set<Race> getRaces() {
        return races;
    }

    public void setRaces(Set<Race> races) {
        this.races = races;
    }

    public CountryOfBirth getCountryOfBirth() {
        return countryOfBirth;
    }

    public void setCountryOfBirth(CountryOfBirth countryOfBirth) {
        this.countryOfBirth = countryOfBirth;
    }

    public Set<Nationality> getCitizenships() {
        return citizenships;
    }

    public void setCitizenships(Set<Nationality> citizenships) {
        this.citizenships = citizenships;
    }

    public Religion getReligion() {
        return religion;
    }

    public void setReligion(Religion religion) {
        this.religion = religion;
    }

    public Religiosity getReligiosity() {
        return religiosity;
    }

    public void setReligiosity(Religiosity religiosity) {
        this.religiosity = religiosity;
    }

    public PoliticalPersuasion getPoliticalPersuasion() {
        return politicalPersuasion;
    }

    public void setPoliticalPersuasion(PoliticalPersuasion politicalPersuasion) {
        this.politicalPersuasion = politicalPersuasion;
    }

    public EducationLevel getEducation() {
        return education;
    }

    public void setEducation(EducationLevel education) {
        this.education = education;
    }

    public OccupationStatus getOccupation() {
        return occupation;
    }

    public void setOccupation(OccupationStatus occupation) {
        this.occupation = occupation;
    }

    public EmploymentSector getEmploymentSector() {
        return employmentSector;
    }

    public void setEmploymentSector(EmploymentSector employmentSector) {
        this.employmentSector = employmentSector;
    }

    public UniversitySubject getUniversitySubject() {
        return universitySubject;
    }

    public void setUniversitySubject(UniversitySubject universitySubject) {
        this.universitySubject = universitySubject;
    }

    public IncomeRange getPersonalIncomeRange() {
        return personalIncomeRange;
    }

    public void setPersonalIncomeRange(IncomeRange personalIncomeRange) {
        this.personalIncomeRange = personalIncomeRange;
    }

    public IncomeRange getHouseholdIncomeRange() {
        return householdIncomeRange;
    }

    public void setHouseholdIncomeRange(IncomeRange householdIncomeRange) {
        this.householdIncomeRange = householdIncomeRange;
    }

    public Height getHeight() {
        return height;
    }

    public void setHeight(Height height) {
        this.height = height;
    }

    public WeightRange getWeightRange() {
        return weightRange;
    }

    public void setWeightRange(WeightRange weightRange) {
        this.weightRange = weightRange;
    }

    public EyeColor getEyeColor() {
        return eyeColor;
    }

    public void setEyeColor(EyeColor eyeColor) {
        this.eyeColor = eyeColor;
    }

    public Parent getParent() {
        return parent;
    }

    public void setParent(Parent parent) {
        this.parent = parent;
    }

    public Integer getNewsFrequency() {
        return newsFrequency;
    }

    public void setNewsFrequency(Integer newsFrequency) {
        this.newsFrequency = newsFrequency;
    }

    public Boolean getHasPet() {
        return hasPet;
    }

    public void setHasPet(Boolean hasPet) {
        this.hasPet = hasPet;
    }

    public Set<PetType> getPetTypes() {
        return petTypes;
    }

    public void setPetTypes(Set<PetType> petTypes) {
        this.petTypes = petTypes;
    }

    public Chronotype getChronotype() {
        return chronotype;
    }

    public void setChronotype(Chronotype chronotype) {
        this.chronotype = chronotype;
    }

    public Outlook getOutlook() {
        return outlook;
    }

    public void setOutlook(Outlook outlook) {
        this.outlook = outlook;
    }

    public Boolean getNeurodivergent() {
        return neurodivergent;
    }

    public void setNeurodivergent(Boolean neurodivergent) {
        this.neurodivergent = neurodivergent;
    }

    public Set<NeurodivergenceType> getNeurodivergenceTypes() {
        return neurodivergenceTypes;
    }

    public void setNeurodivergenceTypes(Set<NeurodivergenceType> neurodivergenceTypes) {
        this.neurodivergenceTypes = neurodivergenceTypes;
    }

    public Boolean getHasDisability() {
        return hasDisability;
    }

    public void setHasDisability(Boolean hasDisability) {
        this.hasDisability = hasDisability;
    }

    public Set<DisabilityType> getDisabilityTypes() {
        return disabilityTypes;
    }

    public void setDisabilityTypes(Set<DisabilityType> disabilityTypes) {
        this.disabilityTypes = disabilityTypes;
    }

    public HousingStatus getHousingStatus() {
        return housingStatus;
    }

    public void setHousingStatus(HousingStatus housingStatus) {
        this.housingStatus = housingStatus;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public Boolean getBalancedNewsViewpoint() {
        return balancedNewsViewpoint;
    }

    public void setBalancedNewsViewpoint(Boolean balancedNewsViewpoint) {
        this.balancedNewsViewpoint = balancedNewsViewpoint;
    }

    public Integer getMainstreamNewsPercent() {
        return mainstreamNewsPercent;
    }

    public void setMainstreamNewsPercent(Integer mainstreamNewsPercent) {
        this.mainstreamNewsPercent = mainstreamNewsPercent;
    }

    public Boolean getBetterWorldWithData() {
        return betterWorldWithData;
    }

    public void setBetterWorldWithData(Boolean betterWorldWithData) {
        this.betterWorldWithData = betterWorldWithData;
    }
}

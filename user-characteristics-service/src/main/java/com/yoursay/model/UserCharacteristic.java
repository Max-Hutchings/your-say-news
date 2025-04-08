package com.yoursay.model;

import com.yoursay.model.Enums.*;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name="user_characteristic")
public class UserCharacteristic extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String postcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UKCounty ukCounty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Race raceEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncomeRange incomeRangeEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CountryOfBirth countryOfBirthEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PoliticalPersuasion politicalPersuasionEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SexAtBirth sexAtBirthEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Height heightEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EyeColor eyeColorEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WeightRange weightRangeEnum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Parent parentEnum;

    @Column(nullable = false)
    private boolean universityEducated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UniversitySubject universitySubjectEnum;

    @Column(nullable = false)
    private boolean propertyOwner;


    public UserCharacteristic(Long id, Long userId, String postcode, UKCounty ukCounty, Race raceEnum, IncomeRange incomeRangeEnum, CountryOfBirth countryOfBirthEnum, PoliticalPersuasion politicalPersuasionEnum, SexAtBirth sexAtBirthEnum, Height heightEnum, EyeColor eyeColorEnum, WeightRange weightRangeEnum, Parent parentEnum, boolean universityEducated, UniversitySubject universitySubjectEnum, boolean propertyOwner) {
        this.id = id;
        this.userId = userId;
        this.postcode = postcode;
        this.ukCounty = ukCounty;
        this.raceEnum = raceEnum;
        this.incomeRangeEnum = incomeRangeEnum;
        this.countryOfBirthEnum = countryOfBirthEnum;
        this.politicalPersuasionEnum = politicalPersuasionEnum;
        this.sexAtBirthEnum = sexAtBirthEnum;
        this.heightEnum = heightEnum;
        this.eyeColorEnum = eyeColorEnum;
        this.weightRangeEnum = weightRangeEnum;
        this.parentEnum = parentEnum;
        this.universityEducated = universityEducated;
        this.universitySubjectEnum = universitySubjectEnum;
        this.propertyOwner = propertyOwner;
    }



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

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public UKCounty getUkCounty() {
        return ukCounty;
    }

    public void setUkCounty(UKCounty ukCounty) {
        this.ukCounty = ukCounty;
    }

    public Race getRaceEnum() {
        return raceEnum;
    }

    public void setRaceEnum(Race raceEnum) {
        this.raceEnum = raceEnum;
    }

    public IncomeRange getIncomeRangeEnum() {
        return incomeRangeEnum;
    }

    public void setIncomeRangeEnum(IncomeRange incomeRangeEnum) {
        this.incomeRangeEnum = incomeRangeEnum;
    }

    public CountryOfBirth getCountryOfBirthEnum() {
        return countryOfBirthEnum;
    }

    public void setCountryOfBirthEnum(CountryOfBirth countryOfBirthEnum) {
        this.countryOfBirthEnum = countryOfBirthEnum;
    }

    public PoliticalPersuasion getPoliticalPersuasionEnum() {
        return politicalPersuasionEnum;
    }

    public void setPoliticalPersuasionEnum(PoliticalPersuasion politicalPersuasionEnum) {
        this.politicalPersuasionEnum = politicalPersuasionEnum;
    }

    public SexAtBirth getSexAtBirthEnum() {
        return sexAtBirthEnum;
    }

    public void setSexAtBirthEnum(SexAtBirth sexAtBirthEnum) {
        this.sexAtBirthEnum = sexAtBirthEnum;
    }

    public Height getHeightEnum() {
        return heightEnum;
    }

    public void setHeightEnum(Height heightEnum) {
        this.heightEnum = heightEnum;
    }

    public EyeColor getEyeColorEnum() {
        return eyeColorEnum;
    }

    public void setEyeColorEnum(EyeColor eyeColorEnum) {
        this.eyeColorEnum = eyeColorEnum;
    }

    public WeightRange getWeightRangeEnum() {
        return weightRangeEnum;
    }

    public void setWeightRangeEnum(WeightRange weightRangeEnum) {
        this.weightRangeEnum = weightRangeEnum;
    }

    public Parent getParentEnum() {
        return parentEnum;
    }

    public void setParentEnum(Parent parentEnum) {
        this.parentEnum = parentEnum;
    }

    public boolean isUniversityEducated() {
        return universityEducated;
    }

    public void setUniversityEducated(boolean universityEducated) {
        this.universityEducated = universityEducated;
    }

    public UniversitySubject getUniversitySubjectEnum() {
        return universitySubjectEnum;
    }

    public void setUniversitySubjectEnum(UniversitySubject universitySubjectEnum) {
        this.universitySubjectEnum = universitySubjectEnum;
    }

    public boolean isPropertyOwner() {
        return propertyOwner;
    }

    public void setPropertyOwner(boolean propertyOwner) {
        this.propertyOwner = propertyOwner;
    }
}

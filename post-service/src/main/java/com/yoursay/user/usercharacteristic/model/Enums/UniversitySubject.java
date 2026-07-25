package com.yoursay.user.usercharacteristic.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum UniversitySubject {
    @Deprecated
    NA,
    SCIENCE,
    ENGINEERING,
    ARTS,
    MEDICINE,
    BUSINESS,
    LAW,
    COMPUTER_SCIENCE,
    MATHEMATICS,
    PHYSICS,
    CHEMISTRY,
    BIOLOGY,
    ECONOMICS,
    PSYCHOLOGY,
    SOCIOLOGY,
    POLITICAL_SCIENCE,
    PHILOSOPHY,
    LITERATURE,
    HISTORY,
    GEOGRAPHY,
    EDUCATION,
    NURSING,
    OSTEOPATHY,
    ARCHITECTURE,
    ENVIRONMENTAL_SCIENCE,
    JOURNALISM,
    FINE_ARTS,
    MUSIC,
    THEATER,
    ANTHROPOLOGY,
    LINGUISTICS,
    ASTRONOMY,
    AGRICULTURE,
    ACCOUNTING_FINANCE,
    ALLIED_HEALTH,
    CRIMINOLOGY,
    DATA_SCIENCE,
    DENTISTRY,
    DESIGN,
    EARTH_SCIENCE_GEOLOGY,
    HOSPITALITY_TOURISM,
    INTERDISCIPLINARY_STUDIES,
    INTERNATIONAL_RELATIONS,
    LANGUAGES,
    MARKETING,
    MEDIA_COMMUNICATIONS,
    PHARMACY,
    PUBLIC_HEALTH,
    SOCIAL_WORK,
    SPORTS_SCIENCE,
    THEOLOGY_RELIGIOUS_STUDIES,
    VETERINARY_SCIENCE,
    OTHER;


    @JsonCreator
    public UniversitySubject fromValue(String value){
        return UniversitySubject.valueOf(value.toUpperCase());
    }
}

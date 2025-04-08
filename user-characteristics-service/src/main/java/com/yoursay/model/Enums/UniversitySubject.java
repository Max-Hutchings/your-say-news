package com.yoursay.model.Enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum UniversitySubject {
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
    OTHER;


    @JsonCreator
    public UniversitySubject fromValue(String value){
        return UniversitySubject.valueOf(value.toUpperCase());
    }
}

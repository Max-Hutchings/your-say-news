package com.yoursay.unwrapped.selection;

import com.yoursay.votes.dto.CohortDimensionV1;

import java.util.List;
import java.util.Locale;

final class CohortDisplayNames {
    private CohortDisplayNames() {
    }

    static String describe(List<CohortDimensionV1> dimensions) {
        return dimensions.stream().map(CohortDisplayNames::describe).reduce((left, right) ->
                left + " and " + lowerFirst(right)).orElse("Selected voters");
    }

    private static String describe(CohortDimensionV1 dimension) {
        String bucket = dimension.bucket();
        return switch (dimension.axis()) {
            case "ageRange" -> age(bucket);
            case "gender" -> switch (bucket) {
                case "MAN" -> "Men";
                case "WOMAN" -> "Women";
                case "NON_BINARY" -> "Non-binary people";
                default -> title(bucket) + " voters";
            };
            case "politicalPersuasion" -> political(bucket);
            case "country" -> "Voters in " + title(bucket);
            case "region" -> "Voters in " + title(bucket);
            case "urbanRural" -> title(bucket) + " voters";
            case "personalIncomeRange" -> income(dimension, "personal");
            case "householdIncomeRange" -> income(dimension, "household");
            case "education" -> education(bucket);
            case "occupation" -> occupation(bucket);
            case "employmentSector" -> employmentSector(bucket) + " workers";
            default -> title(bucket) + " voters";
        };
    }

    private static String age(String bucket) {
        if (bucket.startsWith("AGE_")) {
            String value = bucket.substring(4);
            if (value.endsWith("_PLUS")) {
                return "People aged " + value.substring(0, value.length() - 5) + " and over";
            }
            return value.replace('_', '–') + "-year-olds";
        }
        return title(bucket) + " voters";
    }

    private static String political(String bucket) {
        return switch (bucket) {
            case "LEFT" -> "Left-leaning voters";
            case "CENTRE_LEFT" -> "Centre-left voters";
            case "CENTRE" -> "Centrist voters";
            case "CENTRE_RIGHT" -> "Centre-right voters";
            case "RIGHT" -> "Right-leaning voters";
            case "NOT_POLITICAL", "APOLITICAL" -> "Non-political voters";
            case "NOT_SURE" -> "Politically undecided voters";
            default -> title(bucket) + " voters";
        };
    }

    private static String occupation(String bucket) {
        return switch (bucket) {
            case "EMPLOYED_FULL_TIME" -> "Full-time workers";
            case "EMPLOYED_PART_TIME" -> "Part-time workers";
            case "SELF_EMPLOYED" -> "Self-employed workers";
            case "WORKING_AND_STUDYING", "EMPLOYED_AND_STUDYING" -> "Working students";
            case "CASUAL_GIG_TEMP" -> "Casual and gig workers";
            case "UNEMPLOYED_LOOKING" -> "Jobseekers";
            case "CARER_HOMEMAKER" -> "Carers and homemakers";
            case "UNABLE_TO_WORK_HEALTH" -> "People unable to work for health reasons";
            case "NOT_WORKING_NOT_LOOKING" -> "People outside the workforce";
            case "STUDENT" -> "Students";
            case "RETIRED" -> "Retired people";
            case "EMPLOYED" -> "Employed workers";
            case "UNEMPLOYED" -> "Unemployed people";
            default -> title(bucket) + " workers";
        };
    }

    private static String education(String bucket) {
        return switch (bucket) {
            case "BACHELORS" -> "People with bachelor's degrees";
            case "MASTERS" -> "People with master's degrees";
            case "DOCTORATE" -> "People with doctorates";
            case "VOCATIONAL_TECHNICAL" -> "Vocationally qualified people";
            case "NO_FORMAL_QUALIFICATIONS", "NO_FORMAL_EDUCATION" ->
                    "People without formal qualifications";
            default -> "People educated to " + title(bucket);
        };
    }

    private static String employmentSector(String bucket) {
        return switch (bucket) {
            case "IT_TECHNOLOGY" -> "IT and technology";
            case "RETAIL_WHOLESALE" -> "Retail and wholesale";
            case "TRANSPORT_LOGISTICS" -> "Transport and logistics";
            case "FINANCE_INSURANCE" -> "Finance and insurance";
            case "GOVERNMENT_PUBLIC" -> "Government and public-sector";
            case "MEDIA_COMMUNICATIONS" -> "Media and communications";
            case "ENERGY_UTILITIES" -> "Energy and utilities";
            case "SCIENCE_RESEARCH" -> "Science and research";
            case "ARTS_CULTURE" -> "Arts and culture";
            case "MILITARY_DEFENCE" -> "Military and defence";
            default -> title(bucket);
        };
    }

    private static String income(CohortDimensionV1 dimension, String measure) {
        if (dimension.income() == null) {
            throw new IllegalStateException(
                    "Income cohort is missing its resolved country-specific range");
        }
        return "People with annual " + measure + " income of " + dimension.income().label()
                + " in " + marketWithArticle(dimension.income().marketLabel());
    }

    private static String marketWithArticle(String market) {
        return switch (market) {
            case "United Kingdom", "United States", "United Arab Emirates", "Euro area" ->
                    "the " + market;
            default -> market;
        };
    }

    private static String title(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String lowerFirst(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}

package com.yoursay.usercharacteristic.service;

import com.yoursay.usercharacteristic.CharacteristicOptionDto;
import com.yoursay.usercharacteristic.CharacteristicOptionsDto;
import com.yoursay.usercharacteristic.model.Enums.*;
import com.yoursay.usercharacteristic.model.EnumOptionPolicy;
import com.yoursay.usercharacteristic.model.UserCharacteristicRules;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the curated public option catalogue from the domain enums. */
@ApplicationScoped
public class CharacteristicOptionsCatalog {

    public static final int SCHEMA_VERSION = 1;

    public CharacteristicOptionsDto getOptions() {
        Map<String, List<CharacteristicOptionDto>> fields = new LinkedHashMap<>();

        fields.put("urbanRural", options(UrbanRural.values()));
        fields.put("gender", options(Gender.values(), labels(
                "NON_BINARY", "Non-binary",
                "SELF_DESCRIBE", "Another gender identity")));
        fields.put("sexAtBirth", options(SexAtBirth.values()));
        fields.put("race", options(Race.values(), labels(
                "WHITE_EUROPEAN", "White / European descent",
                "BLACK_AFRICAN", "Black / African descent",
                "MIDDLE_EASTERN_NORTH_AFRICAN", "Middle Eastern / North African",
                "HISPANIC_LATINO", "Hispanic / Latino",
                "INDIGENOUS", "Indigenous / First Nations",
                "MIXED_MULTIPLE", "Mixed or multiple backgrounds",
                "OTHER_ETHNIC_GROUP", "Other ethnic background",
                "SELF_DESCRIBE", "Prefer to self-describe")));
        fields.put("sexualOrientation", options(SexualOrientation.values(), labels(
                "STRAIGHT_HETEROSEXUAL", "Straight / heterosexual",
                "GAY_LESBIAN", "Gay or lesbian",
                "QUESTIONING", "Questioning / unsure",
                "SELF_DESCRIBE", "Another orientation")));
        fields.put("maritalStatus", options(MaritalStatus.values(), labels(
                "IN_RELATIONSHIP", "Dating / in a relationship",
                "COHABITING", "Living with a partner",
                "CIVIL_PARTNERSHIP", "Civil partnership",
                "DIVORCED_OR_DISSOLVED", "Divorced / dissolved civil partnership",
                "WIDOWED_OR_SURVIVING_PARTNER", "Widowed / surviving civil partner")));

        Map<String, String> countryLabels = countryLabels();
        fields.put("countryOfBirth", options(CountryOfBirth.values(), countryLabels));
        fields.put("citizenship", nationalityOptions(countryLabels));
        fields.put("ukCounty", options(UKCounty.values(), labels(
                "TYNE_AND_WEAR", "Tyne and Wear",
                "ARGYLL_AND_BUTE", "Argyll and Bute",
                "DUMFRIES_AND_GALLOWAY", "Dumfries and Galloway",
                "PERTH_AND_KINROSS", "Perth and Kinross",
                "ANTRIM_AND_NEWTOWNABBEY", "Antrim and Newtownabbey",
                "ARDS_AND_NORTH_DOWN", "Ards and North Down",
                "ARMAGH_CITY_BANBRIDGE_AND_CRAIGAVON", "Armagh City, Banbridge and Craigavon",
                "CAUSEWAY_COAST_AND_GLENS", "Causeway Coast and Glens",
                "DERRY_AND_STRABANE", "Derry and Strabane",
                "FERMANAGH_AND_OMAGH", "Fermanagh and Omagh",
                "LISBURN_AND_CASTLEREAGH", "Lisburn and Castlereagh",
                "MID_AND_EAST_ANTRIM", "Mid and East Antrim",
                "NEWRY_MOURNE_AND_DOWN", "Newry, Mourne and Down")));

        fields.put("religion", options(Religion.values(), labels(
                "OTHER_RELIGION", "Other religion",
                "NO_RELIGION", "No religion")));
        fields.put("religiosity", options(Religiosity.values(), labels(
                "NOT_RELIGIOUS", "Not at all important",
                "SLIGHTLY_RELIGIOUS", "Not very important",
                "MODERATELY_RELIGIOUS", "Somewhat important",
                "VERY_RELIGIOUS", "Very important")));
        fields.put("politicalPersuasion", options(PoliticalPersuasion.values(), labels(
                "CENTRE_LEFT", "Centre-left",
                "CENTRE_RIGHT", "Centre-right",
                "NOT_POLITICAL", "Not political",
                "NOT_SURE", "Not sure")));

        fields.put("education", options(EducationLevel.values(), labels(
                "NO_FORMAL_QUALIFICATIONS", "No formal qualifications",
                "PRIMARY_SCHOOLING", "Primary / basic schooling",
                "SECONDARY_SCHOOL", "Secondary school",
                "VOCATIONAL_TECHNICAL", "Vocational / technical qualification",
                "HIGHER_EDUCATION_BELOW_DEGREE", "Higher education below degree",
                "BACHELORS", "Bachelor's degree",
                "MASTERS", "Master's degree",
                "NOT_SURE", "Not sure")));
        fields.put("occupation", options(OccupationStatus.values(), labels(
                "EMPLOYED_FULL_TIME", "Employed full-time",
                "EMPLOYED_PART_TIME", "Employed part-time",
                "WORKING_AND_STUDYING", "Working and studying",
                "SELF_EMPLOYED", "Self-employed",
                "CASUAL_GIG_TEMP", "Casual / gig / temporary work",
                "UNEMPLOYED_LOOKING", "Unemployed and looking",
                "NOT_WORKING_NOT_LOOKING", "Not working, not looking",
                "CARER_HOMEMAKER", "Carer / homemaker",
                "UNABLE_TO_WORK_HEALTH", "Unable to work (health / disability)")));
        fields.put("employmentSector", options(EmploymentSector.values(), labels(
                "MINING_QUARRYING", "Mining & quarrying",
                "RETAIL_WHOLESALE", "Retail & wholesale",
                "TRANSPORT_LOGISTICS", "Transport & logistics",
                "IT_TECHNOLOGY", "IT & technology",
                "FINANCE_INSURANCE", "Finance & insurance",
                "ADMIN_SUPPORT", "Admin & support",
                "GOVERNMENT_PUBLIC", "Government & public",
                "MEDIA_COMMUNICATIONS", "Media & communications",
                "ENERGY_UTILITIES", "Energy & utilities",
                "SCIENCE_RESEARCH", "Science & research",
                "ARTS_CULTURE", "Arts & culture",
                "NONPROFIT", "Non-profit",
                "MILITARY_DEFENCE", "Military & defence",
                "NOT_APPLICABLE", "Not applicable")));
        fields.put("universitySubject", options(UniversitySubject.values(), labels(
                "SCIENCE", "Science (general)",
                "THEATER", "Theatre / drama")));

        fields.put("height", options(Height.values(), labels(
                "FEET_4_0_TO_4_4", "4'0\" – 4'4\" (122–132 cm)",
                "FEET_4_5_TO_4_9", "4'5\" – 4'9\" (135–145 cm)",
                "FEET_4_10_TO_5_0", "4'10\" – 5'0\" (147–152 cm)",
                "FEET_5_1_TO_5_3", "5'1\" – 5'3\" (155–160 cm)",
                "FEET_5_4_TO_5_6", "5'4\" – 5'6\" (163–168 cm)",
                "FEET_5_7_TO_5_9", "5'7\" – 5'9\" (170–175 cm)",
                "FEET_5_10_TO_6_0", "5'10\" – 6'0\" (178–183 cm)",
                "FEET_6_1_TO_6_3", "6'1\" – 6'3\" (185–190 cm)",
                "FEET_6_4_TO_6_6", "6'4\" – 6'6\" (193–198 cm)",
                "FEET_6_7_TO_6_9", "6'7\" – 6'9\" (201–206 cm)",
                "FEET_6_10_TO_7_0", "6'10\" – 7'0\" (208–213 cm)",
                "FEET_7_1_PLUS", "7'1\"+ (216+ cm)")));
        fields.put("weightRange", options(WeightRange.values(), weightLabels()));
        fields.put("incomeRange", options(IncomeRange.values(), incomeLabels()));
        fields.put("eyeColor", options(EyeColor.values(), labels(
                "GRAY", "Grey",
                "BLACK_DARK_BROWN", "Black / very dark brown",
                "OTHER_UNSURE", "Other / not sure")));
        fields.put("parent", options(Parent.values(), labels(
                "NOT_PARENT_CAREGIVER", "Not a parent or caregiver",
                "PARENT_CAREGIVER_UNDER_18", "Parent / caregiver of a child under 18",
                "PARENT_CAREGIVER_ADULT_ONLY", "Parent / caregiver of an adult child only",
                "EXPECTING", "Expecting / soon to be a parent")));

        fields.put("petType", options(PetType.values(), labels(
                "SMALL_MAMMAL", "Small mammal",
                "HORSE_PONY", "Horse / pony")));
        fields.put("chronotype", options(Chronotype.values(), labels(
                "MORNING_LARK", "Mostly morning",
                "NIGHT_OWL", "Mostly evening / night",
                "IN_BETWEEN", "Mixed / depends")));
        fields.put("outlook", options(Outlook.values(), labels(
                "OPTIMIST", "Mostly optimistic",
                "PESSIMIST", "Mostly pessimistic",
                "DEPENDS", "Mixed / depends")));
        fields.put("neurodivergenceType", options(NeurodivergenceType.values()));
        fields.put("disabilityType", options(DisabilityType.values(), labels(
                "PHYSICAL_MOBILITY", "Physical / mobility",
                "COGNITIVE_LEARNING", "Cognitive / learning",
                "CHRONIC_ILLNESS", "Chronic illness",
                "MENTAL_HEALTH", "Mental health")));
        fields.put("housingStatus", options(HousingStatus.values(), labels(
                "OWN_OUTRIGHT", "Own outright",
                "OWN_MORTGAGE", "Own with a mortgage",
                "SHARED_OWNERSHIP", "Shared ownership",
                "PRIVATE_RENT", "Private rent",
                "SOCIAL_RENT", "Social rent",
                "LIVE_WITH_FAMILY", "Live with parents / family",
                "RENT_FREE", "Live rent-free",
                "STUDENT_ACCOMMODATION", "Student / university accommodation",
                "TEMPORARY_NO_FIXED", "Temporary / no fixed address")));
        fields.put("propertyType", options(PropertyType.values(), labels(
                "SEMI_DETACHED", "Semi-detached",
                "FLAT_APARTMENT", "Flat / apartment",
                "ROOM_SHARED_HOUSE", "Room in a shared house",
                "STUDENT_HALLS", "Student halls",
                "MOBILE_TEMPORARY", "Mobile / temporary home",
                "OTHER_UNKNOWN", "Other / unknown")));

        return new CharacteristicOptionsDto(
                SCHEMA_VERSION,
                UserCharacteristicRules.MINIMUM_AGE,
                fields);
    }

    private static List<CharacteristicOptionDto> nationalityOptions(Map<String, String> countryLabels) {
        Map<String, String> labels = new LinkedHashMap<>(countryLabels);
        labels.put("IRELAND", "Irish");
        labels.put("NORTHERN_IRISH", "Northern Irish");
        return options(Nationality.values(), labels).stream()
                .sorted(Comparator.comparing(CharacteristicOptionDto::label))
                .toList();
    }

    private static Map<String, String> countryLabels() {
        return labels(
                "ANTIGUA_AND_BARBUDA", "Antigua and Barbuda",
                "BOSNIA_AND_HERZEGOVINA", "Bosnia and Herzegovina",
                "CONGO_DEMOCRATIC_REPUBLIC", "Congo, Democratic Republic of",
                "CONGO_REPUBLIC", "Congo, Republic of",
                "COTE_DIVOIRE", "Côte d’Ivoire",
                "GUINEA_BISSAU", "Guinea-Bissau",
                "SAINT_KITTS_AND_NEVIS", "Saint Kitts and Nevis",
                "SAINT_VINCENT_AND_THE_GRENADINES", "Saint Vincent and the Grenadines",
                "SAO_TOME_AND_PRINCIPE", "Sao Tome and Principe",
                "TIMOR_LESTE", "Timor-Leste",
                "TRINIDAD_AND_TOBAGO", "Trinidad and Tobago");
    }

    private static Map<String, String> weightLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("KG_UNDER_40", "Under 40 kg");
        for (int lower = 40; lower <= 140; lower += 10) {
            labels.put("KG_" + lower + "_" + (lower + 9), lower + "–" + (lower + 9) + " kg");
        }
        labels.put("KG_150_PLUS", "150+ kg");
        return labels;
    }

    private static Map<String, String> incomeLabels() {
        return labels(
                "BELOW_20K", "Under 20k",
                "BETWEEN_20K_AND_30K", "20k–30k",
                "BETWEEN_30K_AND_40K", "30k–40k",
                "BETWEEN_40K_AND_50K", "40k–50k",
                "BETWEEN_50K_AND_75K", "50k–75k",
                "BETWEEN_75K_AND_100K", "75k–100k",
                "BETWEEN_100K_AND_150K", "100k–150k",
                "BETWEEN_150K_AND_200K", "150k–200k",
                "BETWEEN_200K_AND_500K", "200k–500k",
                "BETWEEN_500K_AND_1000K", "500k–1M",
                "ABOVE_1000000", "1M or more");
    }

    private static <E extends Enum<E>> List<CharacteristicOptionDto> options(E[] values) {
        return options(values, Map.of());
    }

    private static <E extends Enum<E>> List<CharacteristicOptionDto> options(
            E[] values, Map<String, String> labels) {
        return Arrays.stream(values)
                .filter(EnumOptionPolicy::isOffered)
                .map(value -> new CharacteristicOptionDto(
                        labels.getOrDefault(value.name(), humanize(value.name())),
                        value.name()))
                .toList();
    }

    private static String humanize(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        List<String> result = new ArrayList<>(words.length);
        for (String word : words) {
            result.add(switch (word) {
                case "adhd" -> "ADHD";
                case "it" -> "IT";
                default -> Character.toUpperCase(word.charAt(0)) + word.substring(1);
            });
        }
        return String.join(" ", result);
    }

    private static Map<String, String> labels(String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Option label entries must be key/value pairs");
        }
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            labels.put(entries[i], entries[i + 1]);
        }
        return labels;
    }
}

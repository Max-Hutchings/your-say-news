package com.yoursay.user.usercharacteristic;

import com.yoursay.user.usercharacteristic.dto.IncomeCatalogDto;

import com.yoursay.user.usercharacteristic.dto.CharacteristicOptionDto;

import com.yoursay.user.usercharacteristic.service.CharacteristicOptionsCatalog;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CharacteristicOptionsCatalogTest {

    @Inject
    CharacteristicOptionsCatalog catalog;

    @Test
    void categoricalOptionsAreAlphabeticalWhileScalesAndRangesKeepTheirMeaningfulOrder() {
        Map<String, List<CharacteristicOptionDto>> fields = catalog.getOptions().fields();
        Set<String> meaningfullyOrderedFields = Set.of(
                "religiosity",
                "politicalPersuasion",
                "education",
                "height",
                "weightRange",
                "incomeRange");

        fields.forEach((field, options) -> {
            if (meaningfullyOrderedFields.contains(field)) {
                return;
            }
            List<String> actualLabels = options.stream()
                    .map(CharacteristicOptionDto::label)
                    .toList();
            List<String> expectedLabels = new ArrayList<>(actualLabels);
            expectedLabels.sort(String.CASE_INSENSITIVE_ORDER);
            assertEquals(expectedLabels, actualLabels, field + " should be alphabetical");
        });

        assertEquals(
                List.of("NOT_RELIGIOUS", "SLIGHTLY_RELIGIOUS", "MODERATELY_RELIGIOUS", "VERY_RELIGIOUS"),
                values(fields, "religiosity"));
        assertEquals(
                List.of("LEFT", "CENTRE_LEFT", "CENTRE", "CENTRE_RIGHT", "RIGHT", "NOT_POLITICAL", "NOT_SURE"),
                values(fields, "politicalPersuasion"));
        assertEquals(
                List.of(
                        "NO_FORMAL_QUALIFICATIONS", "PRIMARY_SCHOOLING", "SECONDARY_SCHOOL",
                        "VOCATIONAL_TECHNICAL", "HIGHER_EDUCATION_BELOW_DEGREE", "BACHELORS",
                        "MASTERS", "DOCTORATE", "OTHER", "NOT_SURE"),
                values(fields, "education"));
        assertEquals(
                List.of(
                        "FEET_4_0_TO_4_4", "FEET_4_5_TO_4_9", "FEET_4_10_TO_5_0",
                        "FEET_5_1_TO_5_3", "FEET_5_4_TO_5_6", "FEET_5_7_TO_5_9",
                        "FEET_5_10_TO_6_0", "FEET_6_1_TO_6_3", "FEET_6_4_TO_6_6",
                        "FEET_6_7_TO_6_9", "FEET_6_10_TO_7_0", "FEET_7_1_PLUS"),
                values(fields, "height"));
        assertEquals(
                List.of(
                        "KG_UNDER_40", "KG_40_49", "KG_50_59", "KG_60_69", "KG_70_79",
                        "KG_80_89", "KG_90_99", "KG_100_109", "KG_110_119", "KG_120_129",
                        "KG_130_139", "KG_140_149", "KG_150_PLUS"),
                values(fields, "weightRange"));
        assertEquals(
                List.of(
                        "BELOW_20K", "BETWEEN_20K_AND_30K", "BETWEEN_30K_AND_40K",
                        "BETWEEN_40K_AND_50K", "BETWEEN_50K_AND_75K", "BETWEEN_75K_AND_100K",
                        "BETWEEN_100K_AND_150K", "BETWEEN_150K_AND_200K", "BETWEEN_200K_AND_500K",
                        "BETWEEN_500K_AND_1000K", "ABOVE_1000000"),
                values(fields, "incomeRange"));
    }

    @Test
    void universitySubjectsIncludeCommonAdditionalDegreesWithStableValues() {
        assertEquals(
                List.of(
                        option("Accounting & finance", "ACCOUNTING_FINANCE"),
                        option("Agriculture", "AGRICULTURE"),
                        option("Allied health", "ALLIED_HEALTH"),
                        option("Anthropology", "ANTHROPOLOGY"),
                        option("Architecture", "ARCHITECTURE"),
                        option("Arts", "ARTS"),
                        option("Astronomy", "ASTRONOMY"),
                        option("Biology", "BIOLOGY"),
                        option("Business", "BUSINESS"),
                        option("Chemistry", "CHEMISTRY"),
                        option("Computer Science", "COMPUTER_SCIENCE"),
                        option("Criminology", "CRIMINOLOGY"),
                        option("Data science", "DATA_SCIENCE"),
                        option("Dentistry", "DENTISTRY"),
                        option("Design", "DESIGN"),
                        option("Earth science / geology", "EARTH_SCIENCE_GEOLOGY"),
                        option("Economics", "ECONOMICS"),
                        option("Education", "EDUCATION"),
                        option("Engineering", "ENGINEERING"),
                        option("Environmental Science", "ENVIRONMENTAL_SCIENCE"),
                        option("Fine Arts", "FINE_ARTS"),
                        option("Geography", "GEOGRAPHY"),
                        option("History", "HISTORY"),
                        option("Hospitality & tourism", "HOSPITALITY_TOURISM"),
                        option("Interdisciplinary studies", "INTERDISCIPLINARY_STUDIES"),
                        option("International relations", "INTERNATIONAL_RELATIONS"),
                        option("Journalism", "JOURNALISM"),
                        option("Languages", "LANGUAGES"),
                        option("Law", "LAW"),
                        option("Linguistics", "LINGUISTICS"),
                        option("Literature", "LITERATURE"),
                        option("Marketing", "MARKETING"),
                        option("Mathematics", "MATHEMATICS"),
                        option("Media & communications", "MEDIA_COMMUNICATIONS"),
                        option("Medicine", "MEDICINE"),
                        option("Music", "MUSIC"),
                        option("Nursing", "NURSING"),
                        option("Osteopathy", "OSTEOPATHY"),
                        option("Other", "OTHER"),
                        option("Pharmacy", "PHARMACY"),
                        option("Philosophy", "PHILOSOPHY"),
                        option("Physics", "PHYSICS"),
                        option("Political Science", "POLITICAL_SCIENCE"),
                        option("Psychology", "PSYCHOLOGY"),
                        option("Public health", "PUBLIC_HEALTH"),
                        option("Science (general)", "SCIENCE"),
                        option("Social work", "SOCIAL_WORK"),
                        option("Sociology", "SOCIOLOGY"),
                        option("Sports science", "SPORTS_SCIENCE"),
                        option("Theatre / drama", "THEATER"),
                        option("Theology / religious studies", "THEOLOGY_RELIGIOUS_STUDIES"),
                        option("Veterinary science", "VETERINARY_SCIENCE")),
                catalog.getOptions().fields().get("universitySubject"));
    }

    @Test
    void optionsIncludeTheVersionedIncomeProfileDirectory() {
        IncomeCatalogDto incomeCatalog = catalog.getOptions().incomeCatalog();

        assertEquals("2026.1", incomeCatalog.catalogVersion());
        assertTrue(incomeCatalog.profiles().stream().anyMatch(profile ->
                profile.marketCode().equals("GB")
                        && profile.currencyCode().equals("GBP")
                        && profile.residenceCountryCodes().equals(List.of("UNITED_KINGDOM"))));
        assertTrue(incomeCatalog.profiles().stream().anyMatch(profile ->
                profile.marketCode().equals("IN")
                        && profile.currencyCode().equals("INR")
                        && profile.residenceCountryCodes().equals(List.of("INDIA"))));
    }

    private static List<String> values(
            Map<String, List<CharacteristicOptionDto>> fields,
            String field) {
        return fields.get(field).stream()
                .map(CharacteristicOptionDto::value)
                .toList();
    }

    private static CharacteristicOptionDto option(String label, String value) {
        return new CharacteristicOptionDto(label, value);
    }
}

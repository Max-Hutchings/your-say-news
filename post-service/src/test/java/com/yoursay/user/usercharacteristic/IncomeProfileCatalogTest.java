package com.yoursay.user.usercharacteristic;

import com.yoursay.user.usercharacteristic.dto.IncomeAnswerDto;
import com.yoursay.user.usercharacteristic.dto.IncomeBandDto;
import com.yoursay.user.usercharacteristic.dto.IncomeCatalogDto;
import com.yoursay.user.usercharacteristic.dto.IncomeProfileDto;
import com.yoursay.user.usercharacteristic.dto.IncomeProfileSummaryDto;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;
import com.yoursay.user.usercharacteristic.error.UserCharacteristicApiException;
import com.yoursay.user.usercharacteristic.service.IncomeProfileCatalog;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class IncomeProfileCatalogTest {

    @Inject
    IncomeProfileCatalog catalog;

    @Inject
    AgroalDataSource dataSource;

    @Test
    void exposesVersionedProfilesWithSeparateContiguousPersonalAndHouseholdBands() {
        IncomeCatalogDto result = catalog.getCatalog();

        assertEquals("2026.1", result.catalogVersion());
        assertEquals(20, result.profiles().size());
        assertEquals(20, result.profiles().stream().map(IncomeProfileSummaryDto::profileId).distinct().count());
        assertEquals(
                List.of(
                        "GB:GBP", "IN:INR", "US:USD", "CN:CNY", "EU:EUR", "JP:JPY",
                        "CA:CAD", "BR:BRL", "AU:AUD", "KR:KRW", "MX:MXN", "RU:RUB",
                        "CH:CHF", "SG:SGD", "HK:HKD", "ZA:ZAR", "SE:SEK", "NO:NOK",
                        "DK:DKK", "AE:AED"),
                result.profiles().stream()
                        .map(profile -> profile.marketCode() + ":" + profile.currencyCode())
                        .toList());
        Map<String, List<List<Long>>> expectedBoundaries = expectedBoundaries();

        for (IncomeProfileSummaryDto summary : result.profiles()) {
            IncomeProfileDto profile = catalog.find(summary.marketCode(), summary.currencyCode());
            assertNotNull(profile);
            assertEquals(profile.profileId(), summary.profileId());
            assertEquals(profile.profileVersion(), summary.profileVersion());
            assertEquals(profile.marketLabel(), summary.marketLabel());
            assertEquals(profile.residenceCountryCodes(), summary.residenceCountryCodes());
            assertEquals(7, profile.personalBands().size());
            assertEquals(7, profile.householdBands().size());
            assertContiguous(profile.personalBands());
            assertContiguous(profile.householdBands());
            String key = summary.marketCode() + ":" + summary.currencyCode();
            assertEquals(expectedBoundaries.get(key).get(0), upperBoundaries(profile.personalBands()));
            assertEquals(expectedBoundaries.get(key).get(1), upperBoundaries(profile.householdBands()));
            assertEquals(expectedCountries().get(key), profile.residenceCountryCodes());
            assertFalse(profile.sourceYear().isBlank());
            assertTrue(profile.sourceUrl().startsWith("https://"));
            assertFalse(profile.derivation().isBlank());
            assertTrue(List.of("HIGH", "MEDIUM").contains(profile.confidence()));
        }

        IncomeProfileDto usd = catalog.find("US", "USD");
        assertEquals("2024", usd.sourceYear());
        assertEquals("MEDIUM", usd.confidence());
        assertEquals("https://api.worldbank.org/v2/indicator/PA.NUS.PPP", usd.sourceUrl());
        assertEquals("World Bank PPP-calibrated fallback, checked and rounded for the local market",
                usd.derivation());
        assertEquals(25_000L, usd.personalBands().get(1).lowerInclusive());
        assertEquals(30_000L, usd.householdBands().get(1).lowerInclusive());
    }

    @Test
    void usesReviewedGbpAndInrCutPointsInsteadOfRelabellingOneNumberScale() {
        IncomeProfileDto gbp = catalog.find("GB", "GBP");
        IncomeProfileDto inr = catalog.find("IN", "INR");

        assertNotNull(gbp);
        assertNotNull(inr);
        assertEquals(
                List.of(
                        "Under GBP 15k", "GBP 15k to GBP 25k", "GBP 25k to GBP 40k",
                        "GBP 40k to GBP 60k", "GBP 60k to GBP 90k", "GBP 90k to GBP 140k",
                        "GBP 140k or more"),
                labels(gbp.personalBands()));
        assertEquals(
                List.of(
                        "Under INR 2 lakh", "INR 2 lakh to INR 4 lakh", "INR 4 lakh to INR 7 lakh",
                        "INR 7 lakh to INR 12 lakh", "INR 12 lakh to INR 20 lakh",
                        "INR 20 lakh to INR 35 lakh", "INR 35 lakh or more"),
                labels(inr.personalBands()));
        assertEquals(15_000L, gbp.personalBands().getFirst().upperExclusive());
        assertEquals(200_000L, inr.personalBands().getFirst().upperExclusive());
        assertEquals(20_000L, gbp.householdBands().getFirst().upperExclusive());
        assertEquals(300_000L, inr.householdBands().getFirst().upperExclusive());
        assertEquals("2025", gbp.sourceYear());
        assertEquals("HIGH", gbp.confidence());
        assertEquals("ONS earnings distribution with rounded household cut points", gbp.derivation());
        assertEquals("https://www.ons.gov.uk/employmentandlabourmarket/peopleinwork/earningsandworkinghours/"
                + "bulletins/annualsurveyofhoursandearnings/latest", gbp.sourceUrl());
        assertEquals("2023-24", inr.sourceYear());
        assertEquals("MEDIUM", inr.confidence());
        assertEquals("PLFS earnings evidence with rounded gross-income bands", inr.derivation());
        assertEquals("https://www.mospi.gov.in/sites/default/files/publication_reports/"
                + "AnnualReport_PLFS2023-24L2.pdf", inr.sourceUrl());
    }

    @Test
    void resolvesOnlyBandsAndProvenanceBelongingToTheSelectedProfile() throws Exception {
        IncomeAnswerDto valid = new IncomeAnswerDto(
                2,
                "2026.1",
                "IN-INR-GROSS-2023-24-v1",
                1,
                "IN",
                "INR",
                "PERSONAL_TIER_3",
                "HOUSEHOLD_TIER_5");

        IncomeProfileCatalog.ResolvedIncomeAnswer resolved = catalog.resolve(valid);
        assertEquals("IN-INR-GROSS-2023-24-v1", resolved.profile().profileId());
        assertEquals(1, resolved.profile().profileVersion());
        assertEquals("PERSONAL_TIER_3", resolved.personalBand().id());
        assertEquals("TIER_3", resolved.personalBand().tier());
        assertEquals("INR 4 lakh to INR 7 lakh", resolved.personalBand().label());
        assertEquals(400_000L, resolved.personalBand().lowerInclusive());
        assertEquals(700_000L, resolved.personalBand().upperExclusive());
        assertEquals("HOUSEHOLD_TIER_5", resolved.householdBand().id());
        assertEquals("TIER_5", resolved.householdBand().tier());
        assertEquals("INR 18 lakh to INR 30 lakh", resolved.householdBand().label());
        assertEquals(1_800_000L, resolved.householdBand().lowerInclusive());
        assertEquals(3_000_000L, resolved.householdBand().upperExclusive());
        assertEquals(profileDatabaseId(valid.profileId()), resolved.profileDatabaseId());
        assertEquals(bandDatabaseId(valid.profileId(), valid.personalBandId()),
                resolved.personalBandDatabaseId());
        assertEquals(bandDatabaseId(valid.profileId(), valid.householdBandId()),
                resolved.householdBandDatabaseId());

        UserCharacteristicApiException missingIncome =
                assertThrows(UserCharacteristicApiException.class, () -> catalog.resolve(null));
        assertEquals("USER_CHARACTERISTIC_REQUIRED_FIELD", missingIncome.errorCode());
        assertTrue(missingIncome.getMessage().contains("field=income"));

        assertInvalid(new IncomeAnswerDto(
                2, "2026.1", valid.profileId(), 1, "IN", "GBP",
                valid.personalBandId(), valid.householdBandId()), "income");
        assertInvalid(new IncomeAnswerDto(
                2, "2026.1", valid.profileId(), 1, "IN", "INR",
                "PERSONAL_TIER_99", valid.householdBandId()), "income.personalBandId");
        assertInvalid(new IncomeAnswerDto(
                2, "2026.1", valid.profileId(), 1, "IN", "INR",
                "HOUSEHOLD_TIER_3", valid.householdBandId()), "income.personalBandId");

        assertInvalid(new IncomeAnswerDto(
                1, valid.catalogVersion(), valid.profileId(), valid.profileVersion(),
                valid.marketCode(), valid.currencyCode(), valid.personalBandId(),
                valid.householdBandId()), "income.answerVersion");
        assertInvalid(new IncomeAnswerDto(
                2, "2025.9", valid.profileId(), valid.profileVersion(),
                valid.marketCode(), valid.currencyCode(), valid.personalBandId(),
                valid.householdBandId()), "income.catalogVersion");
        assertInvalid(new IncomeAnswerDto(
                2, valid.catalogVersion(), valid.profileId(), 2,
                valid.marketCode(), valid.currencyCode(), valid.personalBandId(),
                valid.householdBandId()), "income");
        assertInvalid(new IncomeAnswerDto(
                2, valid.catalogVersion(), valid.profileId(), valid.profileVersion(),
                "GB", valid.currencyCode(), valid.personalBandId(),
                valid.householdBandId()), "income");
        assertInvalid(new IncomeAnswerDto(
                2, valid.catalogVersion(), valid.profileId(), valid.profileVersion(),
                valid.marketCode(), valid.currencyCode(), valid.personalBandId(),
                "HOUSEHOLD_TIER_99"), "income.householdBandId");
    }

    @Test
    void restrictsCountriesWithLocalProfilesToTheirOwnMarketProfile() {
        IncomeProfileDto gbp = catalog.find("GB", "GBP");
        IncomeProfileDto inr = catalog.find("IN", "INR");

        assertTrue(catalog.isResidenceCompatible("UNITED_KINGDOM", gbp));
        assertFalse(catalog.isResidenceCompatible("UNITED_KINGDOM", inr));
        assertTrue(catalog.isResidenceCompatible("INDIA", inr));
        assertFalse(catalog.isResidenceCompatible("INDIA", gbp));
        assertTrue(catalog.isResidenceCompatible("NEPAL", inr));
    }

    @Test
    void leavesLegacyIncomeBucketsUnresolvedForCompatibilityFallbacks() {
        assertNull(catalog.resolveDisplay("LEGACY_TIER_3"));
        assertNull(catalog.resolveDisplay(null));
    }

    @Test
    @TestTransaction
    void activatingAReplacementAddsANewActiveRowAndPreservesTheOldRange() throws Exception {
        long previousProfileId = profileDatabaseId("GB-GBP-GROSS-2025-v1");
        long replacementId;
        try (Connection connection = dataSource.getConnection()) {
            replacementId = insertReplacementProfile(connection);
        }

        IncomeProfileDto activated = catalog.activate("GB-GBP-GROSS-2027-v2");

        assertEquals("GB-GBP-GROSS-2027-v2", activated.profileId());
        assertEquals(2, activated.profileVersion());
        assertEquals(List.of(20_000L, 30_000L, 45_000L, 65_000L, 95_000L, 145_000L),
                upperBoundaries(activated.personalBands()));
        assertEquals("https://www.ons.gov.uk/ashe-2027", activated.sourceUrl());
        assertEquals("Reviewed replacement fixture", activated.derivation());
        assertEquals("GB-GBP-GROSS-2027-v2", catalog.find("GB", "GBP").profileId());
        assertEquals(1, catalog.getCatalog().profiles().stream()
                .filter(profile -> profile.marketCode().equals("GB"))
                .count());
        assertFalse(active("GB-GBP-GROSS-2025-v1"));
        assertTrue(active("GB-GBP-GROSS-2027-v2"));
        assertEquals(replacementId, profileDatabaseId("GB-GBP-GROSS-2027-v2"));
        assertEquals(previousProfileId, supersededProfileId("GB-GBP-GROSS-2027-v2"));
        assertTrue(hasDeactivationTimestamp("GB-GBP-GROSS-2025-v1"));

        IncomeRangeDisplayDto historical = catalog.resolveDisplay(
                "income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3");
        assertEquals("income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
                historical.bucketId());
        assertEquals("GBP 25k to GBP 40k", historical.label());
        assertEquals("Annual personal income before tax in the United Kingdom",
                historical.contextLabel());
        assertEquals("25th to 50th percentile locally", historical.relativeLabel());
        assertEquals(25_000L, historical.lowerInclusive());
        assertEquals(40_000L, historical.upperExclusive());
        assertEquals("GB", historical.marketCode());
        assertEquals("United Kingdom", historical.marketLabel());
        assertEquals("GBP", historical.currencyCode());
        assertEquals("PERSONAL", historical.measure());
        assertEquals("Annual personal income before tax", historical.measureLabel());
        assertEquals("TIER_3", historical.relativeTier());
        assertEquals("GB-GBP-GROSS-2025-v1", historical.profileId());
        assertEquals(1, historical.profileVersion());
        assertEquals("PERSONAL_TIER_3", historical.bandId());
        assertNull(catalog.resolveDisplay(
                "salary|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3"));

        IncomeAnswerDto oldAnswer = new IncomeAnswerDto(
                2, "2026.1", "GB-GBP-GROSS-2025-v1", 1, "GB", "GBP",
                "PERSONAL_TIER_3", "HOUSEHOLD_TIER_3");
        assertInvalid(oldAnswer, "income.profileId");
    }

    @Test
    void publishedBandBoundariesCannotBeEditedInPlace() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement update = connection.prepareStatement("""
                        UPDATE income_range_band b
                        SET upper_exclusive = 16000
                        FROM income_range_profile p
                        WHERE b.income_range_profile_id = p.id
                          AND p.public_id = 'GB-GBP-GROSS-2025-v1'
                          AND b.band_code = 'PERSONAL_TIER_1'
                        """)) {
            assertThrows(java.sql.SQLException.class, update::executeUpdate);
        }
        IncomeRangeDisplayDto unchanged = catalog.resolveDisplay(
                "income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_1");
        assertNull(unchanged.lowerInclusive());
        assertEquals(15_000L, unchanged.upperExclusive());
    }

    private long insertReplacementProfile(Connection connection) throws Exception {
        long id;
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO income_range_profile (
                    public_id, profile_key, version, active, market_code, market_label,
                    currency_code, income_basis, personal_definition, household_definition,
                    source_year, effective_from, created_at, updated_at)
                SELECT 'GB-GBP-GROSS-2027-v2', profile_key, 2, false, market_code, market_label,
                       currency_code, income_basis, personal_definition, household_definition,
                       '2027', DATE '2027-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM income_range_profile
                WHERE public_id = 'GB-GBP-GROSS-2025-v1'
                RETURNING id
                """)) {
            try (ResultSet result = insert.executeQuery()) {
                assertTrue(result.next());
                id = result.getLong(1);
                assertFalse(result.next());
            }
        }
        try (PreparedStatement countries = connection.prepareStatement("""
                INSERT INTO income_range_profile_country (income_range_profile_id, country_code)
                VALUES (?, 'UNITED_KINGDOM')
                """);
                PreparedStatement source = connection.prepareStatement("""
                INSERT INTO income_range_profile_source (
                    income_range_profile_id, publisher, dataset, source_url, retrieved_at,
                    derivation, confidence)
                VALUES (?, 'Office for National Statistics', 'ASHE 2027',
                        'https://www.ons.gov.uk/ashe-2027', DATE '2027-08-01',
                        'Reviewed replacement fixture', 'HIGH')
                """);
                PreparedStatement bands = connection.prepareStatement("""
                INSERT INTO income_range_band (
                    income_range_profile_id, band_code, measure, display_order,
                    lower_inclusive, upper_exclusive, relative_tier)
                SELECT ?, band_code, measure, display_order,
                       CASE WHEN lower_inclusive IS NULL THEN NULL ELSE lower_inclusive + 5000 END,
                       CASE WHEN upper_exclusive IS NULL THEN NULL ELSE upper_exclusive + 5000 END,
                       relative_tier
                FROM income_range_band b
                JOIN income_range_profile p ON p.id = b.income_range_profile_id
                WHERE p.public_id = 'GB-GBP-GROSS-2025-v1'
                """)) {
            countries.setLong(1, id);
            source.setLong(1, id);
            bands.setLong(1, id);
            assertEquals(1, countries.executeUpdate());
            assertEquals(1, source.executeUpdate());
            assertEquals(14, bands.executeUpdate());
        }
        return id;
    }

    private boolean active(String publicId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT active FROM income_range_profile WHERE public_id = ?")) {
            query.setString(1, publicId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                boolean active = result.getBoolean(1);
                assertFalse(result.next());
                return active;
            }
        }
    }

    private long profileDatabaseId(String publicId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT id FROM income_range_profile WHERE public_id = ?")) {
            query.setString(1, publicId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                long id = result.getLong(1);
                assertFalse(result.next());
                return id;
            }
        }
    }

    private long bandDatabaseId(String publicId, String bandId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement("""
                        SELECT b.id
                        FROM income_range_band b
                        JOIN income_range_profile p ON p.id = b.income_range_profile_id
                        WHERE p.public_id = ? AND b.band_code = ?
                        """)) {
            query.setString(1, publicId);
            query.setString(2, bandId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                long id = result.getLong(1);
                assertFalse(result.next());
                return id;
            }
        }
    }

    private long supersededProfileId(String publicId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT supersedes_profile_id FROM income_range_profile WHERE public_id = ?")) {
            query.setString(1, publicId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                long id = result.getLong(1);
                assertFalse(result.wasNull());
                assertFalse(result.next());
                return id;
            }
        }
    }

    private boolean hasDeactivationTimestamp(String publicId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT deactivated_at IS NOT NULL FROM income_range_profile WHERE public_id = ?")) {
            query.setString(1, publicId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                boolean hasTimestamp = result.getBoolean(1);
                assertFalse(result.next());
                return hasTimestamp;
            }
        }
    }

    private static List<String> labels(List<IncomeBandDto> bands) {
        return bands.stream().map(IncomeBandDto::label).toList();
    }

    private static List<Long> upperBoundaries(List<IncomeBandDto> bands) {
        return bands.stream().map(IncomeBandDto::upperExclusive)
                .filter(Objects::nonNull).toList();
    }

    private static Map<String, List<List<Long>>> expectedBoundaries() {
        return Map.ofEntries(
                bounds("GB:GBP", List.of(15_000L, 25_000L, 40_000L, 60_000L, 90_000L, 140_000L),
                        List.of(20_000L, 35_000L, 55_000L, 85_000L, 130_000L, 200_000L)),
                bounds("IN:INR", List.of(200_000L, 400_000L, 700_000L, 1_200_000L, 2_000_000L, 3_500_000L),
                        List.of(300_000L, 600_000L, 1_000_000L, 1_800_000L, 3_000_000L, 5_000_000L)),
                bounds("US:USD", List.of(25_000L, 40_000L, 60_000L, 90_000L, 135_000L, 210_000L),
                        List.of(30_000L, 55_000L, 85_000L, 130_000L, 195_000L, 300_000L)),
                bounds("CN:CNY", List.of(40_000L, 70_000L, 120_000L, 180_000L, 260_000L, 410_000L),
                        List.of(60_000L, 100_000L, 160_000L, 250_000L, 380_000L, 580_000L)),
                bounds("EU:EUR", List.of(15_000L, 30_000L, 45_000L, 70_000L, 100_000L, 160_000L),
                        List.of(25_000L, 40_000L, 60_000L, 95_000L, 145_000L, 225_000L)),
                bounds("JP:JPY", List.of(1_500_000L, 3_000_000L, 4_500_000L, 7_000_000L, 10_000_000L, 16_000_000L),
                        List.of(2_500_000L, 4_000_000L, 6_500_000L, 9_500_000L, 15_000_000L, 23_000_000L)),
                bounds("CA:CAD", List.of(25_000L, 45_000L, 70_000L, 105_000L, 155_000L, 245_000L),
                        List.of(35_000L, 60_000L, 95_000L, 145_000L, 225_000L, 345_000L)),
                bounds("BR:BRL", List.of(20_000L, 35_000L, 60_000L, 90_000L, 135_000L, 210_000L),
                        List.of(30_000L, 50_000L, 80_000L, 125_000L, 195_000L, 300_000L)),
                bounds("AU:AUD", List.of(30_000L, 50_000L, 80_000L, 125_000L, 185_000L, 290_000L),
                        List.of(40_000L, 70_000L, 115_000L, 175_000L, 265_000L, 410_000L)),
                bounds("KR:KRW", List.of(15_000_000L, 25_000_000L, 35_000_000L, 55_000_000L, 80_000_000L, 130_000_000L),
                        List.of(20_000_000L, 30_000_000L, 50_000_000L, 80_000_000L, 120_000_000L, 185_000_000L)),
                bounds("MX:MXN", List.of(80_000L, 140_000L, 240_000L, 360_000L, 540_000L, 840_000L),
                        List.of(120_000L, 200_000L, 320_000L, 500_000L, 780_000L, 1_200_000L)),
                bounds("RU:RUB", List.of(350_000L, 550_000L, 850_000L, 1_300_000L, 1_950_000L, 3_050_000L),
                        List.of(450_000L, 750_000L, 1_200_000L, 1_850_000L, 2_850_000L, 4_350_000L)),
                bounds("CH:CHF", List.of(20_000L, 35_000L, 60_000L, 90_000L, 130_000L, 205_000L),
                        List.of(30_000L, 50_000L, 80_000L, 125_000L, 190_000L, 295_000L)),
                bounds("SG:SGD", List.of(20_000L, 30_000L, 50_000L, 75_000L, 110_000L, 175_000L),
                        List.of(25_000L, 45_000L, 70_000L, 105_000L, 160_000L, 250_000L)),
                bounds("HK:HKD", List.of(80_000L, 140_000L, 240_000L, 360_000L, 540_000L, 820_000L),
                        List.of(120_000L, 200_000L, 320_000L, 500_000L, 780_000L, 1_180_000L)),
                bounds("ZA:ZAR", List.of(80_000L, 120_000L, 200_000L, 300_000L, 460_000L, 700_000L),
                        List.of(100_000L, 180_000L, 280_000L, 420_000L, 660_000L, 1_000_000L)),
                bounds("SE:SEK", List.of(170_000L, 290_000L, 460_000L, 690_000L, 1_040_000L, 1_610_000L),
                        List.of(230_000L, 400_000L, 630_000L, 980_000L, 1_500_000L, 2_300_000L)),
                bounds("NO:NOK", List.of(210_000L, 340_000L, 550_000L, 830_000L, 1_240_000L, 1_930_000L),
                        List.of(280_000L, 480_000L, 760_000L, 1_170_000L, 1_790_000L, 2_750_000L)),
                bounds("DK:DKK", List.of(140_000L, 230_000L, 360_000L, 550_000L, 820_000L, 1_280_000L),
                        List.of(180_000L, 320_000L, 500_000L, 770_000L, 1_180_000L, 1_820_000L)),
                bounds("AE:AED", List.of(40_000L, 70_000L, 110_000L, 170_000L, 250_000L, 390_000L),
                        List.of(60_000L, 100_000L, 150_000L, 240_000L, 360_000L, 560_000L)));
    }

    private static Map.Entry<String, List<List<Long>>> bounds(
            String key, List<Long> personal, List<Long> household) {
        return Map.entry(key, List.of(personal, household));
    }

    private static Map<String, List<String>> expectedCountries() {
        return Map.ofEntries(
                Map.entry("GB:GBP", List.of("UNITED_KINGDOM")),
                Map.entry("IN:INR", List.of("INDIA")),
                Map.entry("US:USD", List.of("UNITED_STATES")),
                Map.entry("CN:CNY", List.of("CHINA")),
                Map.entry("EU:EUR", List.of("AUSTRIA", "BELGIUM", "CROATIA", "CYPRUS", "ESTONIA",
                        "FINLAND", "FRANCE", "GERMANY", "GREECE", "IRELAND", "ITALY", "LATVIA",
                        "LITHUANIA", "LUXEMBOURG", "MALTA", "NETHERLANDS", "PORTUGAL", "SLOVAKIA",
                        "SLOVENIA", "SPAIN")),
                Map.entry("JP:JPY", List.of("JAPAN")), Map.entry("CA:CAD", List.of("CANADA")),
                Map.entry("BR:BRL", List.of("BRAZIL")), Map.entry("AU:AUD", List.of("AUSTRALIA")),
                Map.entry("KR:KRW", List.of("SOUTH_KOREA")), Map.entry("MX:MXN", List.of("MEXICO")),
                Map.entry("RU:RUB", List.of("RUSSIA")), Map.entry("CH:CHF", List.of("SWITZERLAND")),
                Map.entry("SG:SGD", List.of("SINGAPORE")), Map.entry("HK:HKD", List.of()),
                Map.entry("ZA:ZAR", List.of("SOUTH_AFRICA")), Map.entry("SE:SEK", List.of("SWEDEN")),
                Map.entry("NO:NOK", List.of("NORWAY")), Map.entry("DK:DKK", List.of("DENMARK")),
                Map.entry("AE:AED", List.of("UNITED_ARAB_EMIRATES")));
    }

    private static void assertContiguous(List<IncomeBandDto> bands) {
        assertNull(bands.getFirst().lowerInclusive());
        for (int index = 1; index < bands.size(); index++) {
            assertEquals(bands.get(index - 1).upperExclusive(), bands.get(index).lowerInclusive());
        }
        assertNull(bands.getLast().upperExclusive());
    }

    private void assertInvalid(IncomeAnswerDto invalid, String expectedField) {
        UserCharacteristicApiException error =
                assertThrows(UserCharacteristicApiException.class, () -> catalog.resolve(invalid));
        assertEquals("USER_CHARACTERISTIC_INVALID_FIELD", error.errorCode());
        assertTrue(error.getMessage().contains("field=" + expectedField));
    }
}

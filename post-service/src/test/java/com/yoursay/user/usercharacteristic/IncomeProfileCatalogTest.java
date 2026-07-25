package com.yoursay.user.usercharacteristic;

import com.yoursay.user.usercharacteristic.error.UserCharacteristicApiException;
import com.yoursay.user.usercharacteristic.service.IncomeProfileCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IncomeProfileCatalogTest {

    private final IncomeProfileCatalog catalog = new IncomeProfileCatalog();

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
        Map<String, List<Long>> expectedFirstUpperBounds = Map.ofEntries(
                Map.entry("GB:GBP", List.of(15_000L, 20_000L)),
                Map.entry("IN:INR", List.of(200_000L, 300_000L)),
                Map.entry("US:USD", List.of(25_000L, 30_000L)),
                Map.entry("CN:CNY", List.of(40_000L, 60_000L)),
                Map.entry("EU:EUR", List.of(15_000L, 25_000L)),
                Map.entry("JP:JPY", List.of(1_500_000L, 2_500_000L)),
                Map.entry("CA:CAD", List.of(25_000L, 35_000L)),
                Map.entry("BR:BRL", List.of(20_000L, 30_000L)),
                Map.entry("AU:AUD", List.of(30_000L, 40_000L)),
                Map.entry("KR:KRW", List.of(15_000_000L, 20_000_000L)),
                Map.entry("MX:MXN", List.of(80_000L, 120_000L)),
                Map.entry("RU:RUB", List.of(350_000L, 450_000L)),
                Map.entry("CH:CHF", List.of(20_000L, 30_000L)),
                Map.entry("SG:SGD", List.of(20_000L, 25_000L)),
                Map.entry("HK:HKD", List.of(80_000L, 120_000L)),
                Map.entry("ZA:ZAR", List.of(80_000L, 100_000L)),
                Map.entry("SE:SEK", List.of(170_000L, 230_000L)),
                Map.entry("NO:NOK", List.of(210_000L, 280_000L)),
                Map.entry("DK:DKK", List.of(140_000L, 180_000L)),
                Map.entry("AE:AED", List.of(40_000L, 60_000L)));

        for (IncomeProfileSummaryDto summary : result.profiles()) {
            IncomeProfileDto profile = catalog.find(summary.marketCode(), summary.currencyCode());
            assertNotNull(profile);
            assertEquals(7, profile.personalBands().size());
            assertEquals(7, profile.householdBands().size());
            assertContiguous(profile.personalBands());
            assertContiguous(profile.householdBands());
            assertEquals(
                    expectedFirstUpperBounds.get(summary.marketCode() + ":" + summary.currencyCode()),
                    List.of(
                            profile.personalBands().getFirst().upperExclusive(),
                            profile.householdBands().getFirst().upperExclusive()));
            assertFalse(profile.sourceYear().isBlank());
            assertTrue(profile.sourceUrl().startsWith("https://"));
            assertFalse(profile.derivation().isBlank());
            assertTrue(List.of("HIGH", "MEDIUM").contains(profile.confidence()));
        }

        IncomeProfileDto usd = catalog.find("US", "USD");
        assertEquals("2024", usd.sourceYear());
        assertEquals("MEDIUM", usd.confidence());
        assertTrue(usd.sourceUrl().contains("worldbank.org"));
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
    }

    @Test
    void resolvesOnlyBandsAndProvenanceBelongingToTheSelectedProfile() {
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
        assertEquals("TIER_3", resolved.personalBand().tier());
        assertEquals("TIER_5", resolved.householdBand().tier());

        assertInvalid(new IncomeAnswerDto(
                2, "2026.1", valid.profileId(), 1, "IN", "GBP",
                valid.personalBandId(), valid.householdBandId()), "income");
        assertInvalid(new IncomeAnswerDto(
                2, "2026.1", valid.profileId(), 1, "IN", "INR",
                "PERSONAL_TIER_99", valid.householdBandId()), "income.personalBandId");

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

    private static List<String> labels(List<IncomeBandDto> bands) {
        return bands.stream().map(IncomeBandDto::label).toList();
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

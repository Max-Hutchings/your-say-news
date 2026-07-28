package com.yoursay.user.usercharacteristic.service;

import com.yoursay.user.usercharacteristic.dto.IncomeBandDto;

import com.yoursay.user.usercharacteristic.dto.IncomeCatalogDto;

import com.yoursay.user.usercharacteristic.dto.IncomeProfileDto;

import com.yoursay.user.usercharacteristic.dto.IncomeProfileSummaryDto;

import com.yoursay.user.usercharacteristic.dto.IncomeAnswerDto;

import com.yoursay.user.usercharacteristic.*;
import com.yoursay.user.usercharacteristic.error.UserCharacteristicApiException;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Immutable backend-owned income profiles.
 *
 * <p>GB and IN use reviewed local cut points. Other launch profiles are conservative PPP-calibrated
 * fallbacks derived separately from the GB personal and household bases using World Bank 2024
 * {@code PA.NUS.PPP} values, then rounded to locally understandable amounts. No runtime data fetch,
 * live FX conversion or exact user income is involved.
 */
@ApplicationScoped
public class IncomeProfileCatalog {

    public static final String CATALOG_VERSION = "2026.1";
    public static final int ANSWER_VERSION = 2;
    private static final double GBP_PPP = 0.664153d;
    private static final long[] GB_PERSONAL = {15_000, 25_000, 40_000, 60_000, 90_000, 140_000};
    private static final long[] GB_HOUSEHOLD = {20_000, 35_000, 55_000, 85_000, 130_000, 200_000};
    private static final String WORLD_BANK_SOURCE =
            "https://api.worldbank.org/v2/indicator/PA.NUS.PPP";

    private final List<IncomeProfileDto> profiles = buildProfiles();

    public IncomeCatalogDto getCatalog() {
        return new IncomeCatalogDto(
                CATALOG_VERSION,
                profiles.stream()
                        .map(profile -> new IncomeProfileSummaryDto(
                                profile.profileId(),
                                profile.profileVersion(),
                                profile.marketCode(),
                                profile.marketLabel(),
                                profile.currencyCode(),
                                profile.residenceCountryCodes()))
                        .toList());
    }

    public IncomeProfileDto find(String marketCode, String currencyCode) {
        if (marketCode == null || currencyCode == null) {
            return null;
        }
        return profiles.stream()
                .filter(profile -> profile.marketCode().equalsIgnoreCase(marketCode)
                        && profile.currencyCode().equalsIgnoreCase(currencyCode))
                .findFirst()
                .orElse(null);
    }

    public ResolvedIncomeAnswer resolve(IncomeAnswerDto answer) {
        if (answer == null) {
            throw UserCharacteristicApiException.requiredField("income");
        }
        if (!Objects.equals(answer.answerVersion(), ANSWER_VERSION)) {
            throw UserCharacteristicApiException.invalidField("income.answerVersion", "must be 2");
        }
        if (!CATALOG_VERSION.equals(answer.catalogVersion())) {
            throw UserCharacteristicApiException.invalidField(
                    "income.catalogVersion", "is not accepted for new answers");
        }
        IncomeProfileDto profile = profiles.stream()
                .filter(candidate -> candidate.profileId().equals(answer.profileId()))
                .findFirst()
                .orElseThrow(() -> UserCharacteristicApiException.invalidField(
                        "income.profileId", "profile is unknown"));
        if (!Objects.equals(answer.profileVersion(), profile.profileVersion())
                || !profile.marketCode().equals(answer.marketCode())
                || !profile.currencyCode().equals(answer.currencyCode())) {
            throw UserCharacteristicApiException.invalidField(
                    "income", "profile version, market and currency must match the selected profile");
        }
        IncomeBandDto personal = requireBand(
                profile.personalBands(), answer.personalBandId(), "income.personalBandId");
        IncomeBandDto household = requireBand(
                profile.householdBands(), answer.householdBandId(), "income.householdBandId");
        return new ResolvedIncomeAnswer(profile, personal, household);
    }

    public boolean isResidenceCompatible(String countryCode, IncomeProfileDto selectedProfile) {
        boolean hasLocalProfile = profiles.stream()
                .anyMatch(profile -> profile.residenceCountryCodes().contains(countryCode));
        return !hasLocalProfile || selectedProfile.residenceCountryCodes().contains(countryCode);
    }

    private static IncomeBandDto requireBand(
            List<IncomeBandDto> bands,
            String bandId,
            String field) {
        return bands.stream()
                .filter(band -> band.id().equals(bandId))
                .findFirst()
                .orElseThrow(() -> UserCharacteristicApiException.invalidField(
                        field, "band does not belong to the selected profile"));
    }

    private static List<IncomeProfileDto> buildProfiles() {
        List<IncomeProfileDto> result = new ArrayList<>();
        result.add(direct(
                "GB", "United Kingdom", "GBP", List.of("UNITED_KINGDOM"),
                GB_PERSONAL, GB_HOUSEHOLD, "2025",
                "https://www.ons.gov.uk/employmentandlabourmarket/peopleinwork/earningsandworkinghours/"
                        + "bulletins/annualsurveyofhoursandearnings/latest",
                "ONS earnings distribution with rounded household cut points", "HIGH"));
        result.add(direct(
                "IN", "India", "INR", List.of("INDIA"),
                new long[]{200_000, 400_000, 700_000, 1_200_000, 2_000_000, 3_500_000},
                new long[]{300_000, 600_000, 1_000_000, 1_800_000, 3_000_000, 5_000_000},
                "2023-24",
                "https://www.mospi.gov.in/sites/default/files/publication_reports/AnnualReport_PLFS2023-24L2.pdf",
                "PLFS earnings evidence with rounded gross-income bands", "MEDIUM"));
        result.add(ppp("US", "United States", "USD", List.of("UNITED_STATES"), 1d, 1d, 5_000));
        result.add(ppp("CN", "China", "CNY", List.of("CHINA"), 3.5253788895d, .55d, 10_000));
        result.add(ppp("EU", "Euro area", "EUR", euroCountries(), .75d, 1d, 5_000));
        result.add(ppp("JP", "Japan", "JPY", List.of("JAPAN"), 94.462599d, .8d, 500_000));
        result.add(ppp("CA", "Canada", "CAD", List.of("CANADA"), 1.150472d, 1d, 5_000));
        result.add(ppp("BR", "Brazil", "BRL", List.of("BRAZIL"), 2.4837101537d, .4d, 5_000));
        result.add(ppp("AU", "Australia", "AUD", List.of("AUSTRALIA"), 1.366527d, 1d, 5_000));
        result.add(ppp("KR", "South Korea", "KRW", List.of("SOUTH_KOREA"), 809.26722d, .75d, 5_000_000));
        result.add(ppp("MX", "Mexico", "MXN", List.of("MEXICO"), 9.916562d, .4d, 20_000));
        result.add(ppp("RU", "Russia", "RUB", List.of("RUSSIA"), 29.0330725994d, .5d, 50_000));
        result.add(ppp("CH", "Switzerland", "CHF", List.of("SWITZERLAND"), .972889d, 1d, 5_000));
        result.add(ppp("SG", "Singapore", "SGD", List.of("SINGAPORE"), .8279116269d, 1d, 5_000));
        result.add(ppp("HK", "Hong Kong", "HKD", List.of(), 5.6217357788d, .7d, 20_000));
        result.add(ppp("ZA", "South Africa", "ZAR", List.of("SOUTH_AFRICA"), 7.4211352928d, .45d, 20_000));
        result.add(ppp("SE", "Sweden", "SEK", List.of("SWEDEN"), 8.490485d, .9d, 10_000));
        result.add(ppp("NO", "Norway", "NOK", List.of("NORWAY"), 9.142143d, 1d, 10_000));
        result.add(ppp("DK", "Denmark", "DKK", List.of("DENMARK"), 6.050211d, 1d, 10_000));
        result.add(ppp(
                "AE", "United Arab Emirates", "AED", List.of("UNITED_ARAB_EMIRATES"),
                2.3269558965d, .8d, 10_000));
        validateProfiles(result);
        return List.copyOf(result);
    }

    private static IncomeProfileDto direct(
            String marketCode,
            String marketLabel,
            String currencyCode,
            List<String> countries,
            long[] personal,
            long[] household,
            String sourceYear,
            String sourceUrl,
            String derivation,
            String confidence) {
        return profile(
                marketCode, marketLabel, currencyCode, countries, personal, household,
                sourceYear, sourceUrl, derivation, confidence);
    }

    private static IncomeProfileDto ppp(
            String marketCode,
            String marketLabel,
            String currencyCode,
            List<String> countries,
            double ppp,
            double distributionCalibration,
            long roundingStep) {
        double multiplier = (ppp / GBP_PPP) * distributionCalibration;
        return profile(
                marketCode,
                marketLabel,
                currencyCode,
                countries,
                convert(GB_PERSONAL, multiplier, roundingStep),
                convert(GB_HOUSEHOLD, multiplier, roundingStep),
                "2024",
                WORLD_BANK_SOURCE,
                "World Bank PPP-calibrated fallback, checked and rounded for the local market",
                "MEDIUM");
    }

    private static IncomeProfileDto profile(
            String marketCode,
            String marketLabel,
            String currencyCode,
            List<String> countries,
            long[] personal,
            long[] household,
            String sourceYear,
            String sourceUrl,
            String derivation,
            String confidence) {
        String profileId = marketCode + "-" + currencyCode + "-GROSS-" + sourceYear + "-v1";
        return new IncomeProfileDto(
                CATALOG_VERSION,
                profileId,
                1,
                marketCode,
                marketLabel,
                currencyCode,
                List.copyOf(countries),
                sourceYear,
                sourceUrl,
                derivation,
                confidence,
                bands("PERSONAL", currencyCode, personal),
                bands("HOUSEHOLD", currencyCode, household));
    }

    private static List<IncomeBandDto> bands(String measure, String currency, long[] boundaries) {
        List<IncomeBandDto> result = new ArrayList<>();
        for (int tier = 1; tier <= boundaries.length + 1; tier++) {
            Long lower = tier == 1 ? null : boundaries[tier - 2];
            Long upper = tier > boundaries.length ? null : boundaries[tier - 1];
            String label;
            if (lower == null) {
                label = "Under " + format(currency, upper);
            } else if (upper == null) {
                label = format(currency, lower) + " or more";
            } else {
                label = format(currency, lower) + " to " + format(currency, upper);
            }
            result.add(new IncomeBandDto(
                    measure + "_TIER_" + tier,
                    label,
                    lower,
                    upper,
                    "TIER_" + tier));
        }
        return List.copyOf(result);
    }

    private static String format(String currency, long value) {
        if ("INR".equals(currency)) {
            if (value >= 10_000_000) {
                return "INR " + compact(BigDecimal.valueOf(value, 7)) + " crore";
            }
            if (value >= 100_000) {
                return "INR " + compact(BigDecimal.valueOf(value, 5)) + " lakh";
            }
        }
        if (value >= 1_000_000) {
            return currency + " " + compact(BigDecimal.valueOf(value, 6)) + "M";
        }
        if (value >= 1_000) {
            return currency + " " + compact(BigDecimal.valueOf(value, 3)) + "k";
        }
        return currency + " " + value;
    }

    private static String compact(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static long[] convert(long[] source, double multiplier, long step) {
        long[] converted = new long[source.length];
        for (int index = 0; index < source.length; index++) {
            converted[index] = BigDecimal.valueOf(source[index] * multiplier / step)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact() * step;
        }
        return converted;
    }

    private static List<String> euroCountries() {
        return List.of(
                "AUSTRIA", "BELGIUM", "CROATIA", "CYPRUS", "ESTONIA", "FINLAND", "FRANCE",
                "GERMANY", "GREECE", "IRELAND", "ITALY", "LATVIA", "LITHUANIA", "LUXEMBOURG",
                "MALTA", "NETHERLANDS", "PORTUGAL", "SLOVAKIA", "SLOVENIA", "SPAIN");
    }

    private static void validateProfiles(List<IncomeProfileDto> profiles) {
        Set<String> ids = new HashSet<>();
        for (IncomeProfileDto profile : profiles) {
            if (!ids.add(profile.profileId())) {
                throw new IllegalStateException("Duplicate income profile: " + profile.profileId());
            }
            validateBands(profile.profileId(), profile.personalBands());
            validateBands(profile.profileId(), profile.householdBands());
        }
    }

    private static void validateBands(String profileId, List<IncomeBandDto> bands) {
        if (bands.size() != 7) {
            throw new IllegalStateException("Income profile must expose seven tiers: " + profileId);
        }
        Long previousUpper = null;
        for (int index = 0; index < bands.size(); index++) {
            IncomeBandDto band = bands.get(index);
            if (index == 0 && band.lowerInclusive() != null
                    || index > 0 && !Objects.equals(previousUpper, band.lowerInclusive())) {
                throw new IllegalStateException("Income bands must be contiguous: " + profileId);
            }
            if (band.lowerInclusive() != null && band.upperExclusive() != null
                    && band.lowerInclusive() >= band.upperExclusive()) {
                throw new IllegalStateException("Income band is reversed: " + profileId);
            }
            previousUpper = band.upperExclusive();
        }
        if (previousUpper != null) {
            throw new IllegalStateException("Final income band must be open-ended: " + profileId);
        }
    }

    public record ResolvedIncomeAnswer(
            IncomeProfileDto profile,
            IncomeBandDto personalBand,
            IncomeBandDto householdBand
    ) {
    }
}

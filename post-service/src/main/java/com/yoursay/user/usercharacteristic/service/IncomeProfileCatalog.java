package com.yoursay.user.usercharacteristic.service;

import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.user.usercharacteristic.IncomeRangeDisplayService;
import com.yoursay.user.usercharacteristic.dto.IncomeAnswerDto;
import com.yoursay.user.usercharacteristic.dto.IncomeBandDto;
import com.yoursay.user.usercharacteristic.dto.IncomeCatalogDto;
import com.yoursay.user.usercharacteristic.dto.IncomeProfileDto;
import com.yoursay.user.usercharacteristic.dto.IncomeProfileSummaryDto;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;
import com.yoursay.user.usercharacteristic.error.UserCharacteristicApiException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.jboss.logging.MDC;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Database-backed catalogue of published, versioned country income range profiles. */
@ApplicationScoped
public class IncomeProfileCatalog implements IncomeRangeDisplayService {

    public static final String CATALOG_VERSION = "2026.1";
    public static final int ANSWER_VERSION = 2;
    private static final String DOMAIN = "usercharacteristic";
    private static final String ACTIVATE_PROFILE = "activate_profile";
    private static final String RESOLVE_INCOME_DISPLAY = "resolve_income_display";

    @Inject
    EntityManager entityManager;

    @Inject
    DomainMetrics metrics;

    public IncomeCatalogDto getCatalog() {
        Map<Long, List<String>> countriesByProfile = loadActiveResidenceCountries();
        List<IncomeProfileSummaryDto> profiles = loadActiveProfileSummaries(countriesByProfile);
        return new IncomeCatalogDto(CATALOG_VERSION, profiles);
    }

    private Map<Long, List<String>> loadActiveResidenceCountries() {
        List<?> countryRows = entityManager.createNativeQuery("""
                        SELECT c.income_range_profile_id, c.country_code
                        FROM income_range_profile_country c
                        JOIN income_range_profile p ON p.id = c.income_range_profile_id
                        WHERE p.active = true ORDER BY c.country_code
                        """).getResultList();
        Map<Long, List<String>> countries = new HashMap<>();
        for (Object value : countryRows) {
            Object[] row = (Object[]) value;
            countries.computeIfAbsent(number(row[0]).longValue(), ignored -> new ArrayList<>())
                    .add((String) row[1]);
        }
        return countries;
    }

    private List<IncomeProfileSummaryDto> loadActiveProfileSummaries(
            Map<Long, List<String>> countriesByProfile
    ) {
        List<?> profileRows = entityManager.createNativeQuery("""
                        SELECT id, public_id, version, market_code, market_label, currency_code
                        FROM income_range_profile WHERE active = true ORDER BY id
                        """).getResultList();
        List<IncomeProfileSummaryDto> profiles = new ArrayList<>(profileRows.size());
        for (Object value : profileRows) {
            Object[] row = (Object[]) value;
            long id = number(row[0]).longValue();
            profiles.add(new IncomeProfileSummaryDto(
                    (String) row[1], number(row[2]).intValue(), (String) row[3],
                    (String) row[4], (String) row[5],
                    List.copyOf(countriesByProfile.getOrDefault(id, List.of()))));
        }
        return List.copyOf(profiles);
    }

    public IncomeProfileDto find(String marketCode, String currencyCode) {
        if (marketCode == null || currencyCode == null) {
            return null;
        }
        List<?> ids = entityManager.createNativeQuery("""
                        SELECT id FROM income_range_profile
                        WHERE active = true
                          AND upper(market_code) = upper(:market)
                          AND upper(currency_code) = upper(:currency)
                        """)
                .setParameter("market", marketCode)
                .setParameter("currency", currencyCode)
                .getResultList();
        return ids.isEmpty() ? null : loadProfile(number(ids.getFirst()).longValue());
    }

    public ResolvedIncomeAnswer resolve(IncomeAnswerDto answer) {
        validateAnswerMetadata(answer);
        ProfileRecord record = requireActiveProfile(answer.profileId());
        IncomeProfileDto profile = loadProfile(record.id());
        validateSelectedProfile(answer, profile);
        return resolveSelectedBands(answer, record, profile);
    }

    private static void validateAnswerMetadata(IncomeAnswerDto answer) {
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
    }

    private ProfileRecord requireActiveProfile(String profileId) {
        ProfileRecord record = profileByPublicId(profileId, true);
        if (record == null) {
            throw UserCharacteristicApiException.invalidField("income.profileId", "profile is unknown");
        }
        return record;
    }

    private static void validateSelectedProfile(IncomeAnswerDto answer, IncomeProfileDto profile) {
        if (!Objects.equals(answer.profileVersion(), profile.profileVersion())
                || !profile.marketCode().equals(answer.marketCode())
                || !profile.currencyCode().equals(answer.currencyCode())) {
            throw UserCharacteristicApiException.invalidField(
                    "income", "profile version, market and currency must match the selected profile");
        }
    }

    private ResolvedIncomeAnswer resolveSelectedBands(
            IncomeAnswerDto answer,
            ProfileRecord record,
            IncomeProfileDto profile
    ) {
        BandRecord personalRecord = requireBand(record.id(), answer.personalBandId(),
                "PERSONAL", "income.personalBandId");
        BandRecord householdRecord = requireBand(record.id(), answer.householdBandId(),
                "HOUSEHOLD", "income.householdBandId");
        return new ResolvedIncomeAnswer(
                profile, toDto(personalRecord, record.currencyCode()),
                toDto(householdRecord, record.currencyCode()), record.id(),
                personalRecord.id(), householdRecord.id());
    }

    public boolean isResidenceCompatible(String countryCode, IncomeProfileDto selectedProfile) {
        if (countryCode == null) {
            return false;
        }
        Number localProfiles = (Number) entityManager.createNativeQuery("""
                        SELECT count(*)
                        FROM income_range_profile p
                        JOIN income_range_profile_country c ON c.income_range_profile_id = p.id
                        WHERE p.active = true AND c.country_code = :country
                        """)
                .setParameter("country", countryCode)
                .getSingleResult();
        return localProfiles.longValue() == 0
                || selectedProfile.residenceCountryCodes().contains(countryCode);
    }

    /** Publishes a complete draft and atomically retires the prior profile for its market. */
    @Transactional
    public IncomeProfileDto activate(String publicId) {
        long startedAt = System.nanoTime();
        try {
            IncomeProfileDto profile = activateDraft(publicId);
            recordOperation(ACTIVATE_PROFILE, "success", "none", "none", startedAt);
            return profile;
        } catch (RuntimeException exception) {
            recordFailure(ACTIVATE_PROFILE, exception, startedAt);
            throw exception;
        }
    }

    private IncomeProfileDto activateDraft(String publicId) {
        ProfileRecord candidate = profileByPublicId(publicId, false);
        if (candidate == null) {
            throw new IncomeProfileActivationRejectedException("Income range profile is unknown");
        }
        validateDraft(candidate);
        Long supersededId = lockCurrentProfile(candidate);
        retireSupersededProfile(candidate.id(), supersededId);
        publishProfile(candidate.id(), supersededId);
        entityManager.flush();
        return loadProfile(candidate.id());
    }

    private Long lockCurrentProfile(ProfileRecord candidate) {
        List<?> currentIds = entityManager.createNativeQuery("""
                        SELECT id FROM income_range_profile
                        WHERE active = true
                          AND market_code = :market
                          AND currency_code = :currency
                          AND income_basis = :basis
                        FOR UPDATE
                        """)
                .setParameter("market", candidate.marketCode())
                .setParameter("currency", candidate.currencyCode())
                .setParameter("basis", candidate.incomeBasis())
                .getResultList();
        return currentIds.isEmpty() ? null : number(currentIds.getFirst()).longValue();
    }

    private void retireSupersededProfile(long candidateId, Long supersededId) {
        if (supersededId != null && supersededId != candidateId) {
            entityManager.createNativeQuery("""
                            UPDATE income_range_profile
                            SET active = false, deactivated_at = CURRENT_TIMESTAMP,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = :id
                            """)
                    .setParameter("id", supersededId)
                    .executeUpdate();
        }
    }

    private void publishProfile(long candidateId, Long supersededId) {
        entityManager.createNativeQuery("""
                        UPDATE income_range_profile
                        SET active = true, published_at = COALESCE(published_at, CURRENT_TIMESTAMP),
                            deactivated_at = NULL, supersedes_profile_id = :superseded,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """)
                .setParameter("superseded", supersededId)
                .setParameter("id", candidateId)
                .executeUpdate();
    }

    @Override
    public IncomeRangeDisplayDto resolveDisplay(String bucketId) {
        long startedAt = System.nanoTime();
        try {
            IncomeRangeDisplayDto display = findDisplay(bucketId);
            String outcome = display == null ? "expected_rejection" : "success";
            String errorType = display == null ? "not_found" : "none";
            String errorCode = display == null ? "income_range_not_found" : "none";
            recordOperation(RESOLVE_INCOME_DISPLAY, outcome, errorType, errorCode, startedAt);
            return display;
        } catch (RuntimeException exception) {
            recordFailure(RESOLVE_INCOME_DISPLAY, exception, startedAt);
            throw exception;
        }
    }

    private IncomeRangeDisplayDto findDisplay(String bucketId) {
        BucketIdentity identity = BucketIdentity.parse(bucketId);
        if (identity == null) {
            return null;
        }
        Object[] row = findPublishedDisplayRow(identity);
        return row == null ? null : toDisplayDto(bucketId, row);
    }

    private Object[] findPublishedDisplayRow(BucketIdentity identity) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT p.public_id, p.version, p.market_code, p.market_label,
                               p.currency_code, p.personal_definition, p.household_definition,
                               b.band_code, b.measure, b.lower_inclusive, b.upper_exclusive,
                               b.relative_tier
                        FROM income_range_profile p
                        JOIN income_range_band b ON b.income_range_profile_id = p.id
                        WHERE p.public_id = :profileId AND p.published_at IS NOT NULL
                          AND b.band_code = :bandId AND b.measure = :measure
                        """)
                .setParameter("profileId", identity.profileId())
                .setParameter("bandId", identity.bandId())
                .setParameter("measure", identity.measure())
                .getResultList();
        return rows.isEmpty() ? null : (Object[]) rows.getFirst();
    }

    private static IncomeRangeDisplayDto toDisplayDto(String bucketId, Object[] row) {
        String measure = (String) row[8];
        String measureLabel = "PERSONAL".equals(measure) ? (String) row[5] : (String) row[6];
        Long lower = nullableLong(row[9]);
        Long upper = nullableLong(row[10]);
        return new IncomeRangeDisplayDto(
                bucketId,
                label((String) row[4], lower, upper),
                measureLabel + " in " + marketWithArticle((String) row[3]),
                relativeLabel((String) row[11]),
                (String) row[2], (String) row[3], (String) row[4], measure,
                measureLabel, lower, upper, (String) row[11],
                (String) row[0], number(row[1]).intValue(), (String) row[7]);
    }

    private IncomeProfileDto loadProfile(long profileId) {
        ProfileRecord profile = profileById(profileId);
        if (profile == null) {
            return null;
        }
        List<String> countries = loadResidenceCountries(profileId);
        List<BandRecord> allBands = bands(profileId);
        SourceRecord source = loadPrimarySource(profileId);
        return toProfileDto(profile, countries, allBands, source);
    }

    private List<String> loadResidenceCountries(long profileId) {
        List<?> countryRows = entityManager.createNativeQuery("""
                        SELECT country_code FROM income_range_profile_country
                        WHERE income_range_profile_id = :id ORDER BY country_code
                        """)
                .setParameter("id", profileId)
                .getResultList();
        List<String> countries = new ArrayList<>(countryRows.size());
        for (Object country : countryRows) {
            countries.add((String) country);
        }
        return List.copyOf(countries);
    }

    private SourceRecord loadPrimarySource(long profileId) {
        Object[] source = (Object[]) entityManager.createNativeQuery("""
                        SELECT source_url, derivation, confidence
                        FROM income_range_profile_source
                        WHERE income_range_profile_id = :id ORDER BY id LIMIT 1
                        """)
                .setParameter("id", profileId)
                .getSingleResult();
        return new SourceRecord((String) source[0], (String) source[1], (String) source[2]);
    }

    private static IncomeProfileDto toProfileDto(
            ProfileRecord profile,
            List<String> countries,
            List<BandRecord> bands,
            SourceRecord source
    ) {
        return new IncomeProfileDto(
                CATALOG_VERSION, profile.publicId(), profile.version(), profile.marketCode(),
                profile.marketLabel(), profile.currencyCode(), countries, profile.sourceYear(),
                source.url(), source.derivation(), source.confidence(),
                bands.stream().filter(b -> "PERSONAL".equals(b.measure()))
                        .map(b -> toDto(b, profile.currencyCode())).toList(),
                bands.stream().filter(b -> "HOUSEHOLD".equals(b.measure()))
                        .map(b -> toDto(b, profile.currencyCode())).toList());
    }

    private ProfileRecord profileById(long id) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT id, public_id, version, market_code, market_label, currency_code,
                               income_basis, personal_definition, household_definition, source_year,
                               active, published_at
                        FROM income_range_profile WHERE id = :id
                        """)
                .setParameter("id", id).getResultList();
        return rows.isEmpty() ? null : profile((Object[]) rows.getFirst());
    }

    private ProfileRecord profileByPublicId(String publicId, boolean activeOnly) {
        if (publicId == null) {
            return null;
        }
        String sql = """
                SELECT id, public_id, version, market_code, market_label, currency_code,
                       income_basis, personal_definition, household_definition, source_year,
                       active, published_at
                FROM income_range_profile WHERE public_id = :publicId
                """ + (activeOnly ? " AND active = true" : "");
        List<?> rows = entityManager.createNativeQuery(sql)
                .setParameter("publicId", publicId).getResultList();
        return rows.isEmpty() ? null : profile((Object[]) rows.getFirst());
    }

    private static ProfileRecord profile(Object[] row) {
        return new ProfileRecord(number(row[0]).longValue(), (String) row[1], number(row[2]).intValue(),
                (String) row[3], (String) row[4], (String) row[5], (String) row[6],
                (String) row[7], (String) row[8], (String) row[9], (Boolean) row[10], row[11]);
    }

    private List<BandRecord> bands(long profileId) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT id, band_code, measure, display_order, lower_inclusive,
                               upper_exclusive, relative_tier
                        FROM income_range_band
                        WHERE income_range_profile_id = :id
                        ORDER BY measure DESC, display_order
                        """)
                .setParameter("id", profileId).getResultList();
        List<BandRecord> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            result.add(band((Object[]) row));
        }
        return List.copyOf(result);
    }

    private BandRecord findBand(long profileId, String bandId, String measure) {
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT id, band_code, measure, display_order, lower_inclusive,
                               upper_exclusive, relative_tier
                        FROM income_range_band
                        WHERE income_range_profile_id = :profileId
                          AND band_code = :bandId AND measure = :measure
                        """)
                .setParameter("profileId", profileId)
                .setParameter("bandId", bandId)
                .setParameter("measure", measure)
                .getResultList();
        return rows.isEmpty() ? null : band((Object[]) rows.getFirst());
    }

    private BandRecord requireBand(long profileId, String bandId, String measure, String field) {
        BandRecord band = findBand(profileId, bandId, measure);
        if (band == null) {
            throw UserCharacteristicApiException.invalidField(
                    field, "band does not belong to the selected profile");
        }
        return band;
    }

    private static BandRecord band(Object[] row) {
        return new BandRecord(number(row[0]).longValue(), (String) row[1], (String) row[2],
                number(row[3]).intValue(), nullableLong(row[4]), nullableLong(row[5]), (String) row[6]);
    }

    private void validateDraft(ProfileRecord candidate) {
        requireUnpublishedDraft(candidate);
        validateDraftBands(candidate);
        requireProfileProvenance(candidate.id());
        requireIncreasingVersion(candidate);
    }

    private static void requireUnpublishedDraft(ProfileRecord candidate) {
        if (candidate.active() || candidate.publishedAt() != null) {
            throw new IncomeProfileActivationRejectedException(
                    "Only an unpublished inactive income range profile can be activated");
        }
    }

    private void validateDraftBands(ProfileRecord candidate) {
        List<BandRecord> allBands = bands(candidate.id());
        validateBands(candidate.publicId(), allBands.stream()
                .filter(b -> "PERSONAL".equals(b.measure())).toList());
        validateBands(candidate.publicId(), allBands.stream()
                .filter(b -> "HOUSEHOLD".equals(b.measure())).toList());
    }

    private void requireProfileProvenance(long profileId) {
        Number sourceCount = (Number) entityManager.createNativeQuery("""
                        SELECT count(*) FROM income_range_profile_source
                        WHERE income_range_profile_id = :id
                        """).setParameter("id", profileId).getSingleResult();
        if (sourceCount.longValue() == 0) {
            throw new IncomeProfileActivationRejectedException(
                    "Income range profile must have provenance");
        }
    }

    private void requireIncreasingVersion(ProfileRecord candidate) {
        Number latestVersion = (Number) entityManager.createNativeQuery("""
                        SELECT COALESCE(max(version), 0) FROM income_range_profile
                        WHERE profile_key = (
                          SELECT profile_key FROM income_range_profile WHERE id = :id
                        ) AND published_at IS NOT NULL
                        """).setParameter("id", candidate.id()).getSingleResult();
        if (candidate.version() <= latestVersion.intValue()) {
            throw new IncomeProfileActivationRejectedException(
                    "Income range profile version must increase");
        }
    }

    private void recordFailure(String operation, RuntimeException exception, long startedAt) {
        String outcome = failureOutcome(exception);
        String errorType = errorType(exception);
        String errorCode = failureErrorCode(operation, outcome);
        recordOperation(operation, outcome, errorType, errorCode, startedAt);
        if (!"expected_rejection".equals(outcome)) {
            logFailure(operation, outcome, errorType, errorCode);
        }
    }

    private void recordOperation(
            String operation,
            String outcome,
            String errorType,
            String errorCode,
            long startedAt
    ) {
        metrics.recordOperation(
                DOMAIN, operation, outcome, errorType, errorCode, System.nanoTime() - startedAt);
    }

    private static void logFailure(
            String operation, String outcome, String errorType, String errorCode) {
        MDC.put("domain", DOMAIN);
        MDC.put("operation", operation);
        MDC.put("outcome", outcome);
        MDC.put("error_type", errorType);
        MDC.put("error_code", errorCode);
        try {
            Log.error("Income profile operation failed");
        } finally {
            MDC.remove("domain");
            MDC.remove("operation");
            MDC.remove("outcome");
            MDC.remove("error_type");
            MDC.remove("error_code");
        }
    }

    private static String failureErrorCode(String operation, String outcome) {
        if ("expected_rejection".equals(outcome)) {
            return "income_profile_activation_rejected";
        }
        String subject = ACTIVATE_PROFILE.equals(operation) ? "income_profile" : "income_range";
        String failure = "dependency_error".equals(outcome) ? "database_failure" : "internal_failure";
        return subject + "_" + failure;
    }

    private static String failureOutcome(RuntimeException exception) {
        if (isExpectedRejection(exception)) {
            return "expected_rejection";
        }
        return causedByPersistenceFailure(exception) ? "dependency_error" : "service_error";
    }

    private static String errorType(RuntimeException exception) {
        if (isExpectedRejection(exception)) {
            return "validation";
        }
        return causedByPersistenceFailure(exception) ? "database" : "internal";
    }

    private static boolean isExpectedRejection(RuntimeException exception) {
        return exception instanceof IncomeProfileActivationRejectedException;
    }

    private static boolean causedByPersistenceFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PersistenceException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void validateBands(String profileId, List<BandRecord> bands) {
        if (bands.size() != 7) {
            throw new IncomeProfileActivationRejectedException(
                    "Income range profile must have seven bands per measure: " + profileId);
        }
        Long priorUpper = null;
        for (int index = 0; index < bands.size(); index++) {
            BandRecord band = bands.get(index);
            if (band.displayOrder() != index + 1
                    || index == 0 && band.lowerInclusive() != null
                    || index > 0 && !Objects.equals(priorUpper, band.lowerInclusive())) {
                throw new IncomeProfileActivationRejectedException(
                        "Income range profile bands must be contiguous: " + profileId);
            }
            priorUpper = band.upperExclusive();
        }
        if (priorUpper != null) {
            throw new IncomeProfileActivationRejectedException(
                    "Final income range band must be open-ended: " + profileId);
        }
    }

    private static IncomeBandDto toDto(BandRecord band, String currency) {
        return new IncomeBandDto(band.bandCode(),
                label(currency, band.lowerInclusive(), band.upperExclusive()),
                band.lowerInclusive(), band.upperExclusive(), band.relativeTier());
    }

    private static String label(String currency, Long lower, Long upper) {
        if (lower == null) {
            return "Under " + format(currency, upper);
        }
        if (upper == null) {
            return format(currency, lower) + " or more";
        }
        return format(currency, lower) + " to " + format(currency, upper);
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

    private static String relativeLabel(String tier) {
        return switch (tier) {
            case "TIER_1" -> "Lowest 10% locally";
            case "TIER_2" -> "10th to 25th percentile locally";
            case "TIER_3" -> "25th to 50th percentile locally";
            case "TIER_4" -> "50th to 75th percentile locally";
            case "TIER_5" -> "75th to 90th percentile locally";
            case "TIER_6" -> "90th to 95th percentile locally";
            case "TIER_7" -> "Highest 5% locally";
            default -> tier;
        };
    }

    private static String marketWithArticle(String marketLabel) {
        return switch (marketLabel) {
            case "United Kingdom", "United States", "United Arab Emirates", "Euro area" ->
                    "the " + marketLabel;
            default -> marketLabel;
        };
    }

    private static Number number(Object value) {
        return (Number) value;
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : number(value).longValue();
    }

    public record ResolvedIncomeAnswer(
            IncomeProfileDto profile,
            IncomeBandDto personalBand,
            IncomeBandDto householdBand,
            long profileDatabaseId,
            long personalBandDatabaseId,
            long householdBandDatabaseId
    ) {
    }

    private record ProfileRecord(
            long id, String publicId, int version, String marketCode, String marketLabel,
            String currencyCode, String incomeBasis, String personalDefinition,
            String householdDefinition, String sourceYear, boolean active, Object publishedAt) {
    }

    private record BandRecord(
            long id, String bandCode, String measure, int displayOrder,
            Long lowerInclusive, Long upperExclusive, String relativeTier) {
    }

    private record SourceRecord(String url, String derivation, String confidence) {
    }

    private static final class IncomeProfileActivationRejectedException extends IllegalArgumentException {
        private IncomeProfileActivationRejectedException(String message) {
            super(message);
        }
    }

    private record BucketIdentity(String profileId, String measure, String bandId) {
        private static BucketIdentity parse(String bucketId) {
            if (bucketId == null) {
                return null;
            }
            String[] parts = bucketId.split("\\|", -1);
            if (parts.length != 4 || !"income".equals(parts[0])
                    || !("PERSONAL".equals(parts[2]) || "HOUSEHOLD".equals(parts[2]))) {
                return null;
            }
            return new BucketIdentity(parts[1], parts[2], parts[3]);
        }
    }
}

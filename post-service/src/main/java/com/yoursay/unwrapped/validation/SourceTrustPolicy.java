package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.util.Set;

@ApplicationScoped
public class SourceTrustPolicy {
    private static final Set<String> OFFICIAL_SUFFIXES = Set.of(
            "gov", "gov.uk", "gov.au", "gc.ca", "europa.eu",
            "who.int", "un.org", "worldbank.org", "oecd.org", "imf.org");
    private static final Set<String> ACADEMIC_SUFFIXES =
            Set.of("edu", "edu.au", "ac.uk", "doi.org");
    private static final Set<String> MEDIA_SUFFIXES =
            Set.of("reuters.com", "apnews.com", "bbc.com");

    public void validate(UnwrappedSourceDraftV1 source) {
        String host = URI.create(source.url()).getHost().toLowerCase(java.util.Locale.ROOT);
        Set<String> governed = switch (source.classification()) {
            case OFFICIAL -> OFFICIAL_SUFFIXES;
            case ACADEMIC -> ACADEMIC_SUFFIXES;
            case REPUTABLE_MEDIA -> MEDIA_SUFFIXES;
            case OTHER -> Set.of();
        };
        if (source.classification() != SourceClassification.OTHER
                && governed.stream().noneMatch(suffix ->
                host.equals(suffix) || host.endsWith("." + suffix))) {
            throw new IllegalArgumentException("UNWRAPPED_SOURCE_CLASSIFICATION_INVALID");
        }
    }

    public boolean isHighQuality(UnwrappedSourceDraftV1 source) {
        return source.classification() == SourceClassification.OFFICIAL
                || source.classification() == SourceClassification.ACADEMIC;
    }
}

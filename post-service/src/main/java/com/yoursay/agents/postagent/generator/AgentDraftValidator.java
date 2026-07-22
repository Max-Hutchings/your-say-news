package com.yoursay.agents.postagent.generator;

import com.yoursay.agents.postagent.AgentDraftDto;
import com.yoursay.agents.postagent.AgentSourceDto;
import com.yoursay.agents.postagent.SourcedClaimDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class AgentDraftValidator {

    public void validate(AgentDraftDto draft, List<String> providerCitations) {
        if (draft == null) {
            throw invalid("Provider returned no draft");
        }
        requireText(draft.supportQuestion(), "supportQuestion");
        requireText(draft.imageBrief(), "imageBrief");
        requireText(draft.imageSearchQuery(), "imageSearchQuery");

        Set<String> citations = canonicalUrls(providerCitations, "provider citations");
        Set<String> declaredSources = validateSources(draft.sources(), citations);
        validateClaims("summaryClaims", draft.summaryClaims(), declaredSources, citations);
        validateClaims("caseForClaims", draft.caseForClaims(), declaredSources, citations);
        validateClaims("caseAgainstClaims", draft.caseAgainstClaims(), declaredSources, citations);
    }

    private static Set<String> validateSources(List<AgentSourceDto> sources, Set<String> citations) {
        if (sources == null || sources.size() < 2) {
            throw invalid("Draft must contain at least two sources");
        }
        Set<String> declared = new HashSet<>();
        for (AgentSourceDto source : sources) {
            if (source == null) {
                throw invalid("Draft contains a null source");
            }
            String url = canonicalUrl(source.url());
            requireText(source.title(), "source title");
            requireText(source.publisher(), "source publisher");
            if (!citations.contains(url)) {
                throw invalid("Draft source was not returned in provider citations: " + source.url());
            }
            declared.add(url);
        }
        if (declared.size() < 2) {
            throw invalid("Draft must contain at least two distinct sources");
        }
        return declared;
    }

    private static void validateClaims(String field, List<SourcedClaimDto> claims,
                                       Set<String> declaredSources, Set<String> citations) {
        if (claims == null || claims.isEmpty()) {
            throw invalid(field + " must contain at least one claim");
        }
        for (SourcedClaimDto claim : claims) {
            if (claim == null) {
                throw invalid(field + " contains a null claim");
            }
            requireText(claim.text(), field + " text");
            if (claim.sourceUrls() == null || claim.sourceUrls().isEmpty()) {
                throw invalid(field + " claim has no sources");
            }
            for (String rawUrl : claim.sourceUrls()) {
                String url = canonicalUrl(rawUrl);
                if (!declaredSources.contains(url)) {
                    throw invalid(field + " claim cites an undeclared source: " + rawUrl);
                }
                if (!citations.contains(url)) {
                    throw invalid(field + " claim cites a URL absent from provider citations: " + rawUrl);
                }
            }
        }
    }

    private static Set<String> canonicalUrls(List<String> urls, String field) {
        if (urls == null || urls.isEmpty()) {
            throw invalid(field + " are empty");
        }
        Set<String> result = new HashSet<>();
        for (String url : urls) {
            result.add(canonicalUrl(url));
        }
        return result;
    }

    private static String canonicalUrl(String raw) {
        requireText(raw, "source URL");
        try {
            URI uri = URI.create(raw.trim()).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw invalid("Source URL must use HTTP(S): " + raw);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw invalid("Source URL has no host: " + raw);
            }
            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(scheme, uri.getUserInfo(), host.toLowerCase(Locale.ROOT), uri.getPort(),
                    path, uri.getQuery(), null).toString();
        } catch (GenerationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("Invalid source URL: " + raw);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " is required");
        }
    }

    private static GenerationException invalid(String message) {
        return new GenerationException("AGENT_INVALID_PROVIDER_OUTPUT", message, false);
    }
}

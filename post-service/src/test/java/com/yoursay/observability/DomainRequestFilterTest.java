package com.yoursay.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * These names become metric tags. The two things that matter are that the set of values stays
 * bounded no matter what a caller sends, and that no identifier ever reaches a tag.
 */
class DomainRequestFilterTest {

    @Test
    void buildsOperationNamesWithoutAnEmptyLeadingSegment() {
        assertEquals("GET.feed", DomainRequestFilter.operationFrom("GET", "/feed"));
        assertEquals("POST.votes", DomainRequestFilter.operationFrom("POST", "/votes"));
        assertEquals("GET.api.admin.users", DomainRequestFilter.operationFrom("GET", "/api/admin/users"));
    }

    @Test
    void collapsesEveryIdentifierShapedSegmentSoNoIdBecomesAMetricTag() {
        assertEquals("GET.posts.{id}", DomainRequestFilter.operationFrom("GET", "/posts/2007"));
        assertEquals("GET.votes.{id}.sentiment",
                DomainRequestFilter.operationFrom("GET", "/votes/2007/sentiment"));
        assertEquals("GET.agent.jobs.{id}",
                DomainRequestFilter.operationFrom("GET", "/agent/jobs/3f8b1c2d-4e5a-6b7c-8d9e-0f1a2b3c4d5e"));
        assertEquals("GET.your-say-user.email.{id}",
                DomainRequestFilter.operationFrom("GET", "/your-say-user/email/john.doe@example.com"));
        assertEquals("PUT.api.admin.topic-tags.{id}.active",
                DomainRequestFilter.operationFrom("PUT", "/api/admin/topic-tags/climate-and-energy/active"));
    }

    /**
     * The axis is free-form on the wire and only rejected inside the controller, after this filter
     * has read the URI. If it were kept verbatim, any caller could mint unlimited tag values.
     */
    @Test
    void collapsesTheCharacteristicAxisEvenThoughItIsNotIdShaped() {
        assertEquals("GET.votes.{id}.sentiment.{id}",
                DomainRequestFilter.operationFrom("GET", "/votes/2007/sentiment/age_band"));
        assertEquals("GET.votes.{id}.sentiment.{id}",
                DomainRequestFilter.operationFrom("GET", "/votes/2007/sentiment/not-a-real-axis"));
    }

    /** A scanner probing a public service must not be able to create new metric series. */
    @Test
    void collapsesUnknownPathsSoAProbeCannotCreateNewTagValues() {
        assertEquals("GET.{id}.{id}",
                DomainRequestFilter.operationFrom("GET", "/wp-admin/setup-config.php"));
        assertEquals("GET.{id}.{id}",
                DomainRequestFilter.operationFrom("GET", "/wp-admin/other-probe.php"));
        assertEquals("GET.your-say-user.email.{id}",
                DomainRequestFilter.operationFrom("GET", "/your-say-user/email/john.doe"));
    }

    @Test
    void namesTheRootRequestRatherThanProducingABlankOperation() {
        assertEquals("GET.root", DomainRequestFilter.operationFrom("GET", "/"));
        assertEquals("GET.root", DomainRequestFilter.operationFrom("GET", ""));
        assertEquals("GET.root", DomainRequestFilter.operationFrom("GET", null));
    }

    @Test
    void attributesEachRouteToTheDomainThatOwnsIt() {
        assertEquals("feed", DomainRequestFilter.domainFromPath("/feed"));
        assertEquals("posts", DomainRequestFilter.domainFromPath("/posts/2007"));
        assertEquals("votes", DomainRequestFilter.domainFromPath("/votes/2007/count"));
        assertEquals("topics", DomainRequestFilter.domainFromPath("/topic-tags"));
        assertEquals("user", DomainRequestFilter.domainFromPath("/profiles/me"));
        assertEquals("user", DomainRequestFilter.domainFromPath("/your-say-user/onboarding"));
        assertEquals("usercharacteristic", DomainRequestFilter.domainFromPath("/user-characteristics/me"));
        assertEquals("social", DomainRequestFilter.domainFromPath("/social/following"));
        assertEquals("postagent", DomainRequestFilter.domainFromPath("/agent/jobs"));
        assertEquals("platform", DomainRequestFilter.domainFromPath("/live"));
        assertEquals("platform", DomainRequestFilter.domainFromPath("/q/metrics"));
    }

    /**
     * The admin prefixes are listed before the shorter prefixes they would otherwise be swallowed
     * by, so this pins the ordering the routing table depends on.
     */
    @Test
    void attributesAdminRoutesToTheDomainTheyAdminister() {
        assertEquals("user", DomainRequestFilter.domainFromPath("/api/admin/users"));
        assertEquals("topics", DomainRequestFilter.domainFromPath("/api/admin/topic-tags"));
        assertEquals("unwrapped", DomainRequestFilter.domainFromPath("/api/admin/unwrapped/review"));
    }

    @Test
    void attributesNestedUnwrappedRoutesToUnwrappedRatherThanToPosts() {
        assertEquals("unwrapped", DomainRequestFilter.domainFromPath("/posts/2007/unwrapped"));
        assertEquals("unwrapped", DomainRequestFilter.domainFromPath("/posts/2007/unwrapped/9/follow-up"));
        assertEquals("posts", DomainRequestFilter.domainFromPath("/posts/2007/media"));
    }

    @Test
    void reportsAnUnmappedRouteAsUnknownSoItSurfacesOnTheOverviewDashboard() {
        assertEquals("unknown", DomainRequestFilter.domainFromPath("/something-new"));
        assertEquals("unknown", DomainRequestFilter.domainFromPath(null));
    }
}

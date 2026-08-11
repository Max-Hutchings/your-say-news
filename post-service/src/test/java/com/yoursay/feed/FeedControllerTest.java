package com.yoursay.feed;

import com.yoursay.feed.dto.FeedPage;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The controller's binding contract. Auth (401 anonymous, 403 wrong role) is covered end-to-end by
 * {@code PostControllerAuthTest}; this pins the query parameter names and the size default that the
 * mobile client depends on, neither of which a service-level test would catch.
 */
@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock
    FeedService feedService;

    @Mock
    SecurityIdentity securityIdentity;

    @Test
    void bindsAndForwardsTheCursorSizeTypeAndTopicTagQueryParameters() throws NoSuchMethodException {
        FeedController controller = controller();
        FeedPage expected = new FeedPage(List.of(), "next-cursor-token");
        when(feedService.getFeed("reader@example.com", "Bearer token", "cursor-token", 7,
                FeedPostType.VIDEO, "housing")).thenReturn(Uni.createFrom().item(expected));

        FeedPage result = controller.feed("cursor-token", 7, FeedPostType.VIDEO, "housing", "Bearer token")
                .await().indefinitely();

        assertEquals(expected, result);
        verify(feedService).getFeed(
                "reader@example.com", "Bearer token", "cursor-token", 7, FeedPostType.VIDEO, "housing");
        assertEquals("cursor", parameter(0).getAnnotation(QueryParam.class).value());
        assertEquals("size", parameter(1).getAnnotation(QueryParam.class).value());
        assertEquals("type", parameter(2).getAnnotation(QueryParam.class).value());
        assertEquals("topicTag", parameter(3).getAnnotation(QueryParam.class).value());
    }

    @Test
    void defaultsTheSizeSoABareRequestPagesAtFive() throws NoSuchMethodException {
        // A bare GET /feed must page at 5. Without the default, size binds to 0 and the service
        // would silently fall back — this pins the wire contract the mobile client relies on.
        DefaultValue size = parameter(1).getAnnotation(DefaultValue.class);

        assertEquals("5", size.value());
    }

    @Test
    void feedLogsRecordOnlyNonIdentifyingRequestShape() {
        String log = FeedController.requestLog(7, true);

        assertEquals("Endpoint Called: feed - size 7 categoryFiltered true", log);
    }

    @Test
    void aFirstPageRequestSendsNoCursorOrTopicTagAndReturnsTheEndOfFeedMarker() {
        FeedController controller = controller();
        when(feedService.getFeed("reader@example.com", "Bearer token", null, 5, null, null))
                .thenReturn(Uni.createFrom().item(new FeedPage(List.of(), null)));

        FeedPage result = controller.feed(null, 5, null, null, "Bearer token").await().indefinitely();

        assertEquals(List.of(), result.posts());
        assertEquals(null, result.nextCursor());
        // An absent topic must reach the service as null, not "": a blank filter that leaked into
        // the query would match no post and empty the default feed.
        verify(feedService).getFeed("reader@example.com", "Bearer token", null, 5, null, null);
    }

    private FeedController controller() {
        FeedController controller = new FeedController();
        controller.feedService = feedService;
        controller.securityIdentity = securityIdentity;
        Principal principal = () -> "reader@example.com";
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        return controller;
    }

    private static Parameter parameter(int index) throws NoSuchMethodException {
        Method feedMethod = FeedController.class.getMethod(
                "feed", String.class, int.class, FeedPostType.class, String.class, String.class);
        return feedMethod.getParameters()[index];
    }
}

package com.yoursay.feed;

import com.yoursay.posts.PostDto;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock
    FeedService feedService;

    @Mock
    SecurityIdentity securityIdentity;

    @Test
    void bindsAndForwardsTheTypeQueryParameter() throws NoSuchMethodException {
        FeedController controller = new FeedController();
        controller.feedService = feedService;
        controller.securityIdentity = securityIdentity;
        Principal principal = () -> "reader@example.com";
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(feedService.getFeed("reader@example.com", "Bearer token", 2, 7, FeedPostType.VIDEO))
                .thenReturn(Uni.createFrom().item(List.of()));

        List<PostDto> result = controller.feed(2, 7, FeedPostType.VIDEO, "Bearer token")
                .await().indefinitely();

        assertEquals(List.of(), result);
        verify(feedService).getFeed(
                "reader@example.com", "Bearer token", 2, 7, FeedPostType.VIDEO);

        Method feedMethod = FeedController.class.getMethod(
                "feed", int.class, int.class, FeedPostType.class, String.class);
        QueryParam annotation = feedMethod.getParameters()[2].getAnnotation(QueryParam.class);
        assertEquals("type", annotation.value());
    }
}

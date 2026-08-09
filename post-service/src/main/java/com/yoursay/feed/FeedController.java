package com.yoursay.feed;

import com.yoursay.feed.dto.FeedPage;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/feed")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class FeedController {

    @Inject
    FeedService feedService;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * One page of the reader's feed. {@code cursor} is omitted for the first page and otherwise
     * echoes the previous page's {@code nextCursor}; it is opaque and must not be constructed by a
     * client. A null {@code nextCursor} in the response is the end of the feed.
     *
     * <p>{@code topic} selects a category feed (ADR-043). An unknown topic is rejected with 400
     * rather than returned as an empty page, so a client bug does not read as a dead category.
     */
    @GET
    public Uni<FeedPage> feed(@QueryParam("cursor") String cursor,
                              @QueryParam("size") @DefaultValue("5") int size,
                              @QueryParam("type") FeedPostType postType,
                              @QueryParam("topic") String topic,
                              @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        Log.infof("Endpoint Called: feed - cursor %s size %d type %s topic %s viewer %s",
                cursor, size, postType, topic, email);
        return feedService.getFeed(email, authorization, cursor, size, postType, topic);
    }
}

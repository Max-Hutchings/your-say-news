package com.yoursay.feed;

import com.yoursay.posts.PostDto;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/feed")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class FeedController {

    @Inject
    FeedService feedService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public Uni<List<PostDto>> feed(@QueryParam("page") @DefaultValue("0") int page,
                                   @QueryParam("size") @DefaultValue("5") int size,
                                   @QueryParam("type") FeedPostType postType,
                                   @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        Log.infof("Endpoint Called: feed - page %d size %d type %s viewer %s",
                page, size, postType, email);
        return feedService.getFeed(email, authorization, page, size, postType);
    }
}

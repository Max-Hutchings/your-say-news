package com.yoursay.social;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/social")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class SocialController {

    @Inject
    SocialService socialService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/following")
    public FollowingDto following() {
        return new FollowingDto(socialService.getFollowingUserIds(securityIdentity.getPrincipal().getName()));
    }

    @GET
    @Path("/follows/{userId}")
    public FollowStatusDto status(@PathParam("userId") long userId) {
        return socialService.getStatus(securityIdentity.getPrincipal().getName(), userId);
    }

    @POST
    @Path("/follows/{userId}")
    public FollowStatusDto follow(@PathParam("userId") long userId) {
        return socialService.follow(securityIdentity.getPrincipal().getName(), userId);
    }

    @DELETE
    @Path("/follows/{userId}")
    public FollowStatusDto unfollow(@PathParam("userId") long userId) {
        return socialService.unfollow(securityIdentity.getPrincipal().getName(), userId);
    }
}

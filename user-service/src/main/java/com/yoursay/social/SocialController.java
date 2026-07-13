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

    private static final int MAX_PAGE_SIZE = 50;

    @GET
    @Path("/{userId}/followers")
    public FollowPageDto followers(@PathParam("userId") long userId,
                                   @QueryParam("page") @DefaultValue("0") int page,
                                   @QueryParam("size") @DefaultValue("50") int size) {
        return socialService.listFollowers(viewerEmail(), userId, clampPage(page), clampSize(size));
    }

    @GET
    @Path("/{userId}/following")
    public FollowPageDto followingList(@PathParam("userId") long userId,
                                       @QueryParam("page") @DefaultValue("0") int page,
                                       @QueryParam("size") @DefaultValue("50") int size) {
        return socialService.listFollowing(viewerEmail(), userId, clampPage(page), clampSize(size));
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

    private String viewerEmail() {
        return securityIdentity.getPrincipal().getName();
    }

    private static int clampPage(int page) {
        return Math.max(page, 0);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}

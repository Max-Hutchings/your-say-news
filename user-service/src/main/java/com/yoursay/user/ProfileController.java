package com.yoursay.user;

import com.yoursay.social.SocialService;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/profiles")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class ProfileController {

    @Inject
    YourSayUserService userService;

    @Inject
    SocialService socialService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/me")
    public PublicProfileDto me() {
        YourSayUserDto viewer = userService.getByEmail(securityIdentity.getPrincipal().getName());
        return toProfile(viewer, viewer == null ? null : viewer.id());
    }

    @GET
    @Path("/{userId}")
    public RestResponse<PublicProfileDto> profile(@PathParam("userId") long userId) {
        YourSayUserDto viewer = userService.getByEmail(securityIdentity.getPrincipal().getName());
        YourSayUserDto profile = userService.getById(userId);
        if (profile == null) {
            return RestResponse.noContent();
        }
        return RestResponse.ok(toProfile(profile, viewer == null ? null : viewer.id()));
    }

    private PublicProfileDto toProfile(YourSayUserDto user, Long viewerId) {
        if (user == null) {
            return null;
        }
        return new PublicProfileDto(
                user.id(),
                user.displayName(),
                user.handle(),
                user.avatarUrl(),
                socialService.countFollowers(user.id()),
                socialService.countFollowing(user.id()),
                viewerId != null && socialService.isFollowing(viewerId, user.id())
        );
    }
}

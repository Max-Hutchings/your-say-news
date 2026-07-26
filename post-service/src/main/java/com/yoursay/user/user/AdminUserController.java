package com.yoursay.user.user;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/admin/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"user", "admin"})
@RunOnVirtualThread
public class AdminUserController {

    @Inject
    YourSayUserService userService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public List<AdminUserDto> listUsers() {
        return userService.listForAdmin(subjectEmail());
    }

    @PUT
    @Path("/{userId}")
    public AdminUserDto updateUser(@PathParam("userId") long userId, @Valid AdminUserUpdateDto update) {
        return userService.updateForAdmin(subjectEmail(), userId, update);
    }

    private String subjectEmail() {
        return securityIdentity.getPrincipal().getName();
    }
}

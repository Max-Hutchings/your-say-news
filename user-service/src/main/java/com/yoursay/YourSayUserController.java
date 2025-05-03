
package com.yoursay;

import com.yoursay.model.YourSayUser;
import com.yoursay.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.auth.User;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Path("/api/your-say-user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class YourSayUserController{

    @Inject
    YourSayUserRepository yourSayUserRepository;

    @Inject
    UserSavePreparer userSavePreparer;

    @Inject
    HttpCookieGenerator httpCookieGenerator;




    @POST
    @Path("/sign-up")
    @ResponseStatus(value = 201)
    public Uni<Response> saveYourSayUser(YourSayUser yourSayUser) {
        Log.infof("Endpoint called: Save YourSayUser %s", yourSayUser.getEmail());
        Log.info(yourSayUser.toString());

        userSavePreparer.prepareUserForSave(yourSayUser);

        return yourSayUserRepository.saveYourSayUser(yourSayUser)
                .onItem().transform(savedUser -> {
                    NewCookie authCookie = httpCookieGenerator.generateNewAuthCookie(savedUser);
                    return Response.status(Response.Status.CREATED).cookie(authCookie).entity(savedUser).build();
                })
                .onFailure()
                .invoke(e -> Log.errorf(
                        "Failed to save YourSayUser %s. Exception: %s",
                        yourSayUser.getEmail(), e
                ));
    }



    public static class LoginRequest{
        public String email;
        public String password;
    }

    @POST
    @Path("/login")
    public Uni<Response> login(LoginRequest req) {
        return yourSayUserRepository.findByEmail(req.email)
                // if no user ⇒ 404
                .onItem().ifNull().failWith(
                        new WebApplicationException("User not found",
                                Response.Status.NOT_FOUND))

                // check password
                .flatMap(user -> {
                    if (BCrypt.checkpw(req.password, user.getPassword())) {
                        // good password → emit user

                        NewCookie authCookie = httpCookieGenerator.generateNewAuthCookie(user);
                        return Uni.createFrom().item(Response.status(Response.Status.OK).cookie(authCookie).entity(user).build());
                    } else {
                        // bad password → signal 401
                        return Uni.createFrom().failure(
                                new WebApplicationException("Invalid credentials",
                                        Response.Status.UNAUTHORIZED));
                    }
                })

                // log any unexpected failures
                .onFailure().invoke(e ->
                        Log.errorf("Login failed for %s: %s", req.email, e.getMessage()));
    }



    @GET
    @Path("/email/{email}")
    public Uni<YourSayUser> getYourSayUser(@PathParam(value = "email") String email) {
        Log.infof("Endpoint called: Get YourSayUser %s", email);
        return yourSayUserRepository.findByEmail(email)
                .onItem()
                .invoke(yourSayUser -> Log.infof("User: %s", String.valueOf(yourSayUser)));
    }

    @GET
    @Path("/{id}")
    public Uni<YourSayUser> getYourSayUserById(@PathParam(value = "id") Long id) {
        Log.infof("Endpoint called: Get YourSayUserById %s", id);
        return yourSayUserRepository.findYourSayUserById(id);
    }

    @GET
    @Path("/all")
    public Uni<List<YourSayUser>> getYourSayUsers() {
        return yourSayUserRepository.listAll().onItem().invoke(list -> Log.infof("Get YourSayUsers %s", list));
    }

    @DELETE
    public Uni<YourSayUser> deleteYourSayUser(YourSayUser yourSayUser) {
        return yourSayUserRepository.deleteYourSayUser(yourSayUser.id).onFailure().invoke(e -> Log.errorf("Failed to deactivate YourSayUser %s. Exception: %s", yourSayUser.getEmail(), e));
    }


}

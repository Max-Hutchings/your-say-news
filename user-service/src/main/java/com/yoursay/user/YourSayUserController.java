
package com.yoursay.user;

import com.yoursay.user.model.YourSayUser;
import com.yoursay.user.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.mindrot.jbcrypt.BCrypt;
import java.util.List;

@Path("/your-say-user")
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
        Log.infof("Endpoint called: Save YourSayUser %s", yourSayUser);

        userSavePreparer.prepareUserForSave(yourSayUser);
        Log.infof(yourSayUser.toString());

        Uni<YourSayUser> uniUser = yourSayUserRepository.saveYourSayUser(yourSayUser);
        return
                uniUser.onItem().transform(savedUser -> {
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
        Log.infof("Endpoint called: login %s %s", req.email, req.password);
        Uni<YourSayUser> uniUser = yourSayUserRepository.findByEmail(req.email).onItem().invoke(yourSayUser -> {Log.info(yourSayUser.toString());});

        uniUser// if no user ⇒ 404
                .onItem().ifNull().failWith(
                        new WebApplicationException("User not found",
                                Response.Status.NOT_FOUND));
        return uniUser
                // check password
                .flatMap(user -> {
                    Log.info(req.password);
                    Log.info(user.getPassword());
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


    /**
     * Allows the client to send a request and server return the user if the client already has a HTTP-only cookie
     * with the users id
     * @param userIdCookie
     * @return User - client can log user in
     * @return Unauthorised HTTP Response if no Cookie exists -- client wont log user in
     * @return Bad Request if there is a malfunction with the auth token -- error
     * @return Not found HTTP Response if there is no user with that id -- error
     */
    @GET
    @Path("/check-logged-in")
    public Uni<Response> checkLoggedIn(@CookieParam(HttpCookieGenerator.HTTP_ONLY_COOKIE_NAME) String userIdCookie) {
        Log.infof("Endpoint called: checkLoggedIn");
        if (userIdCookie == null) {
            throw new WebApplicationException("Not authenticated", Response.Status.UNAUTHORIZED);
        }
        Long userId;
        try {
            userId = Long.valueOf(userIdCookie);
        } catch (NumberFormatException e) {
            throw new WebApplicationException("Invalid auth token", Response.Status.BAD_REQUEST);
        }

        // lookup user by ID
        return yourSayUserRepository.findYourSayUserById(userId)
                .onItem().ifNull().failWith(
                        new WebApplicationException("User not found", Response.Status.NOT_FOUND))
                .onItem().transform(user -> {
                    NewCookie authCookie = httpCookieGenerator.generateNewAuthCookie(user);
                    return Response.ok(user).cookie(authCookie).build();
                }
                );
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

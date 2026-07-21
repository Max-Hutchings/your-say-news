package com.yoursay.user;

import com.yoursay.usercharacteristic.UserCharacteristicService;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.time.LocalDate;
import java.util.Map;


@Path("/your-say-user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class YourSayUserController {

    @Inject
    YourSayUserService userService;

    @Inject
    UserCharacteristicService characteristicService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public YourSayUserDto getUser() {
        String email = securityIdentity.getPrincipal().getName();

        JsonWebToken jwt = (JsonWebToken) securityIdentity.getPrincipal();
        String firstName = jwt.getClaim("given_name");
        String lastName = jwt.getClaim("family_name");

        return userService.getOrCreateFromIdentity(email, firstName, lastName);
    }

    /**
     * Whether the authenticated user has finished onboarding: agreed to the privacy promise AND has
     * a characteristic profile. The client uses this to route — a returning, fully-onboarded user
     * goes straight to the feed rather than being sent back through consent or the wizard.
     */
    @GET
    @Path("/onboarding")
    public OnboardingStatusDto getOnboardingStatus() {
        String email = securityIdentity.getPrincipal().getName();
        // A pure status check — never creates the account, so it doesn't depend on name claims being
        // present in the token. An unknown subject simply hasn't onboarded yet.
        YourSayUserDto user = userService.getByEmail(email);
        if (user == null) {
            return new OnboardingStatusDto(false, false, false);
        }

        boolean consented = user.consentedAt() != null;
        boolean hasCharacteristics = characteristicService.getByUserId(user.id()) != null;
        return new OnboardingStatusDto(consented, hasCharacteristics, consented && hasCharacteristics);
    }

    /** PII-free publishing access for the bearer-token subject. */
    @GET
    @Path("/me/access")
    public UserAccessDto getCurrentAccess() {
        return userService.getAccessByEmail(securityIdentity.getPrincipal().getName());
    }


    @GET
    @Path("/data")
    public Response getData() {
        // Return an HTTP 202 Accepted response with an empty JSON object body
        return Response.accepted().entity(Map.of("data", "This worked")).build();
    }


    /**
     * Record the authenticated user's explicit consent to the privacy promise. The body carries the
     * policy version they agreed to; the identity comes from the token.
     */
    @POST
    @Path("/consent")
    public YourSayUserDto recordConsent(Map<String, String> body) {
        String email = securityIdentity.getPrincipal().getName();
        String version = body.getOrDefault("privacyPolicyVersion", "unversioned");
        return userService.recordConsent(email, version);
    }

    @POST
    @Path("/save")
    @ResponseStatus(201)
    public YourSayUserDto saveUser(Map<String, String> body) {
        String email = securityIdentity.getPrincipal().getName();

        String firstName = (String) securityIdentity.getAttribute("given_name");
        String lastName = (String) securityIdentity.getAttribute("family_name");

        LocalDate birthDate = LocalDate.parse(body.get("birthDate"));

        return userService.save(email, firstName, lastName, birthDate);
    }

    /**
     * Resolve a user id to its anonymised {@link UserRefDto}. Deliberately returns no PII: these
     * lookup endpoints are only role-gated, so returning email/name/date-of-birth would let any
     * authenticated caller harvest the whole user base's identity by iterating ids. Cross-service
     * callers only read the id anyway.
     */
    @GET
    @Path("/id/{id}")
    public UserRefDto getUserById(@PathParam(value = "id") long userId) {
        YourSayUserDto user = userService.getById(userId);
        return user == null ? null : new UserRefDto(user.id());
    }


    /**
     * Resolve an email to its anonymised {@link UserRefDto} (used by other services to turn the
     * authenticated caller's email into the internal id). Returns no PII, for the same reason as
     * {@link #getUserById}.
     */
    @GET
    @Path("/email/{email}")
    public UserRefDto getUserByEmail(@PathParam(value = "email") String email) {
        YourSayUserDto user = userService.getByEmail(email);
        return user == null ? null : new UserRefDto(user.id());
    }
}

package com.yoursay.usercharacteristic;

import com.yoursay.user.YourSayUserDto;
import com.yoursay.user.YourSayUserService;
import com.yoursay.usercharacteristic.error.UserCharacteristicApiException;
import com.yoursay.usercharacteristic.service.CharacteristicOptionsCatalog;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ResponseStatus;

/**
 * Characteristic onboarding API.
 *
 * <p><strong>PII boundary:</strong> save/read operate on "the current user", whose id is resolved
 * server-side from the authenticated subject — the answer body never carries identity. Aggregation
 * elsewhere reads characteristics only; it never joins them back to a name or email.
 */
@Path("/user-characteristics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class UserCharacteristicController {

    @Inject
    UserCharacteristicService characteristicService;

    @Inject
    CharacteristicOptionsCatalog optionsCatalog;

    @Inject
    YourSayUserService userService;

    @Inject
    SecurityIdentity securityIdentity;

    /** Save the authenticated user's characteristic profile. */
    @POST
    @ResponseStatus(201)
    public UserCharacteristicDto saveMine(UserCharacteristicDto answers) {
        long userId = currentUserId();
        Log.infof("Saving characteristics for user %d", userId);
        return characteristicService.saveForUser(userId, answers);
    }

    /** Ordered enum-backed choices offered by the current onboarding schema. */
    @GET
    @Path("/options")
    public CharacteristicOptionsDto getOptions() {
        return optionsCatalog.getOptions();
    }

    /** The authenticated user's characteristic profile, or 204 if they have not onboarded. */
    @GET
    @Path("/me")
    public Response getMine() {
        UserCharacteristicDto dto = characteristicService.getByUserId(currentUserId());
        return dto == null ? Response.noContent().build() : Response.ok(dto).build();
    }

    /** Resolve the authenticated subject (email in the token) to our internal user id. */
    private long currentUserId() {
        String email = securityIdentity.getPrincipal().getName();
        YourSayUserDto user = userService.getByEmail(email);
        if (user == null) {
            throw UserCharacteristicApiException.userMissing(email);
        }
        return user.id();
    }
}

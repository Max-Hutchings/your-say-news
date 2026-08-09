package com.yoursay.topics;

import com.yoursay.topics.dto.CreateTopicRequest;
import com.yoursay.topics.dto.TopicActiveUpdate;
import com.yoursay.topics.dto.TopicDto;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

/**
 * Catalogue administration (ADR-043). Admins extend the taxonomy at runtime rather than waiting for
 * a migration, so a story that breaks today can be filed under a topic today.
 *
 * <p>Gated on the Keycloak {@code admin} realm role — the catalogue is controlled, and this is where
 * the control lives now that it is no longer the deploy pipeline.
 */
@Path("/api/admin/topics")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminTopicController {

    @Inject
    TopicService topicService;

    /** The whole catalogue, retired topics included, in tab-strip order. */
    @GET
    public Uni<List<TopicDto>> listTopics() {
        Log.info("Endpoint Called: admin listTopics");
        return topicService.listAll();
    }

    /** Add a topic. The canonical id is derived from the label; a collision is a 409. */
    @POST
    @ResponseStatus(201)
    public Uni<TopicDto> createTopic(@Valid @NotNull CreateTopicRequest request) {
        Log.infof("Endpoint Called: admin createTopic - %s", request.label());
        return topicService.create(request);
    }

    /** Retire or restore a topic. Never deletes: existing assignments survive. */
    @PUT
    @Path("/{topicId}/active")
    public Uni<TopicDto> setActive(@PathParam("topicId") String topicId,
                                   @Valid @NotNull TopicActiveUpdate update) {
        Log.infof("Endpoint Called: admin setTopicActive - %s active=%s", topicId, update.active());
        return topicService.setActive(topicId, update.active());
    }
}

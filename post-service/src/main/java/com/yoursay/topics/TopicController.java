package com.yoursay.topics;

import com.yoursay.topics.dto.TopicDto;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * The reader-facing catalogue. The mobile feed calls this once to build its tab strip and the
 * "More" picker, and the create-post screen uses the same list.
 */
@Path("/topics")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"user", "admin"})
public class TopicController {

    @Inject
    TopicService topicService;

    /** Active topics in tab-strip order. Retired topics are omitted. */
    @GET
    public Uni<List<TopicDto>> listTopics() {
        Log.info("Endpoint Called: listTopics");
        return topicService.listActive();
    }
}

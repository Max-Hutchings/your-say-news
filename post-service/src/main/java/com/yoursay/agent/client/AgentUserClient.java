package com.yoursay.agent.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "user-service")
@Produces(MediaType.APPLICATION_JSON)
public interface AgentUserClient {

    @GET
    @Path("/your-say-user/me/access")
    UserAccess getCurrentUserAccess(@HeaderParam("Authorization") String authorization);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserAccess(Long userId, String accountType, String publisherStatus, boolean canPublish) {

        public boolean isActiveOfficialPublisher() {
            return "OFFICIAL".equals(accountType)
                    && "ACTIVE".equals(publisherStatus)
                    && canPublish;
        }
    }
}

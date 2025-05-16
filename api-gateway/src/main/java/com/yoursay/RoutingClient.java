package com.yoursay;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Set;


@RegisterRestClient(baseUri = "https://your-say.com/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RoutingClient {

    @GET
    @Path("/extensions")
    Set<Extension> getExtensionsById(@QueryParam("id") String id);

    class Extension {
        public String id;
        public String name;
        public String shortName;
        public List<String> keywords;
    }


    @GET
    @Path("/{service}")
    Uni<String> routeGet(
                         @PathParam("service") String service
                         );

    @POST
    @Path("/{service}")
    Uni<String> routePost(
                          @PathParam("service") String service,

                          String body);

    @PUT
    @Path("/{service}")
    Uni<String> routePut(
                         @PathParam("service") String service,

                         String body);

    @DELETE
    @Path("/{service}")
    Uni<String> routeDelete(
                            @PathParam("service") String service,
                            String body);
}

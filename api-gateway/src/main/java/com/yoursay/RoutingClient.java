package com.yoursay;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Set;

/**
 * To use it via injection.
 * <p>
 * {@code
 *
 * @Inject
 * @RestClient MyRemoteService myRemoteService;
 * <p>
 * public void doSomething() {
 * Set<MyRemoteService.Extension> restClientExtensions = myRemoteService.getExtensionsById("io.quarkus:quarkus-hibernate-validator");
 * }
 * }
 */
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
    @Path("/{service}/{url}")
    Uni<String> routeGet(@HeaderParam("Authorization") String token,
                         @PathParam("service") String service,
                         @PathParam("url") String url);

    @POST
    @Path("/{service}/{url}")
    Uni<String> routePost(@HeaderParam("Authorization") String token,
                          @PathParam("service") String service,
                          @PathParam("url") String url,
                          String body);

    @PUT
    @Path("/{service}/{url}")
    Uni<String> routePut(@HeaderParam("Authorization") String token,
                         @PathParam("service") String service,
                         @PathParam("url") String url,
                         String body);

    @DELETE
    @Path("/{service}/{url}")
    Uni<String> routeDelete(@HeaderParam("Authorization") String token,
                            @PathParam("service") String service,
                            @PathParam("url") String url,
                            String body);
}

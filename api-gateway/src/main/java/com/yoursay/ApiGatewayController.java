package com.yoursay;

import io.quarkus.logging.Log;


import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.core.Vertx;
import io.smallrye.mutiny.Uni;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ApiGatewayController {

    // Inject all properties under "services.*" into this Map
    @ConfigProperty(name = "services")
    Map<String, String> serviceUrls;

    WebClient client;

    @Inject
    Vertx vertx;

    @PostConstruct
    void init() {
        client = WebClient.create(vertx);
        Log.infof("Services: %s", serviceUrls);
    }

    private URI getUriTarget(String service, String path) {
        String base = serviceUrls.get(service);
        if (base == null) {
            throw new WebApplicationException("Unknown service: " + service);
        }
        return URI.create(base + "/" + path);
    }

    @GET
    @Path("/{service}/{path:.*}")
    public Uni<Response> getRouter(
            @PathParam("service") String service,
            @PathParam("path")    String path,
            @Context HttpHeaders  headers      // inject incoming headers
    ) {
        // build the target URI from service name and path
        URI target = getUriTarget(service, path);
        Log.infof("getRouter: %s", target);

        // extract the Cookie header (HttpOnly cookies live here)
        String cookieHdr = headers.getHeaderString(HttpHeaders.COOKIE);

        // create the outbound GET request (still mutable)
        HttpRequest<Buffer> req = client
                .get(target.getPort(), target.getHost(), target.getPath());

        // if the client sent cookies, forward them downstream
        if (cookieHdr != null) {
            req = req.putHeader(HttpHeaders.COOKIE, cookieHdr);
        }

        // send the request and transform the response into a JAX-RS Response
        return req
                .send()   // no body for GET
                .onItem()
                .transform(resp -> {
                    // mirror status and headers back to the original caller
                    Response.ResponseBuilder rb = Response.status(resp.statusCode());
                    resp.headers().forEach(h -> rb.header(h.getKey(), h.getValue()));
                    // set the response body as a String
                    return rb.entity(resp.bodyAsString()).build();
                });
    }


    @POST
    @Path("/{service}/{path:.*}")
    public Uni<Response> postRouter(
            @PathParam("service") String service,
            @PathParam("path") String path,
            @Context HttpHeaders headers,
            String body
    ) {

        URI target = getUriTarget(service, path);
        Log.infof("postRouter: %s", target);

        String cookieHdr = headers.getHeaderString(HttpHeaders.COOKIE);

        HttpRequest<Buffer> req = client
                .post(target.getPort(), target.getHost(), target.getPath());

        // if a cookie was present, forward it to the downstream service
        if (cookieHdr != null) {
            req = req.putHeader(HttpHeaders.COOKIE, cookieHdr);
        }

        return req
                .sendBuffer(Buffer.buffer(body))
                .onItem()
                .transform(resp -> {
                    Response.ResponseBuilder rb = Response
                            .status(resp.statusCode());
                    resp.headers()
                            .forEach(e -> rb.header(e.getKey(), e.getValue()));
                    return rb
                            .entity(resp.bodyAsString())
                            .build();
                });
    }
    @PUT
    @Path("/{service}/{path:.*}")
    public Uni<Response> putRouter(
            @PathParam("service") String service,
            @PathParam("path")    String path,
            @Context              HttpHeaders headers, // inject incoming headers
            String                body               // raw JSON body
    ) {
        // build the target URI
        URI target = getUriTarget(service, path);
        Log.infof("putRouter: %s", target);

        // extract incoming Cookie header (for HttpOnly cookies)
        String cookieHdr = headers.getHeaderString(HttpHeaders.COOKIE);

        // create the outbound PUT request
        HttpRequest<Buffer> req = client
                .put(target.getPort(), target.getHost(), target.getPath());

        // forward cookie if present
        if (cookieHdr != null) {
            req = req.putHeader(HttpHeaders.COOKIE, cookieHdr);
        }

        // send the body and transform the response
        return req
                .sendBuffer(Buffer.buffer(body))
                .onItem()
                .transform(resp -> {
                    Response.ResponseBuilder rb = Response.status(resp.statusCode());
                    resp.headers().forEach(h -> rb.header(h.getKey(), h.getValue()));
                    return rb.entity(resp.bodyAsString()).build();
                });
    }

    @DELETE
    @Path("/{service}/{path:.*}")
    public Uni<Response> deleteRouter(
            @PathParam("service") String service,
            @PathParam("path")    String path,
            @Context              HttpHeaders headers  // inject incoming headers
    ) {
        // build the target URI
        URI target = getUriTarget(service, path);
        Log.infof("deleteRouter: %s", target);

        // extract incoming Cookie header
        String cookieHdr = headers.getHeaderString(HttpHeaders.COOKIE);

        // create the outbound DELETE request
        HttpRequest<Buffer> req = client
                .delete(target.getPort(), target.getHost(), target.getPath());

        // forward cookie if present
        if (cookieHdr != null) {
            req = req.putHeader(HttpHeaders.COOKIE, cookieHdr);
        }

        // send the request and transform the response
        return req
                .send() // no body for DELETE
                .onItem()
                .transform(resp -> {
                    Response.ResponseBuilder rb = Response.status(resp.statusCode());
                    resp.headers().forEach(h -> rb.header(h.getKey(), h.getValue()));
                    return rb.entity(resp.bodyAsString()).build();
                });
    }

}

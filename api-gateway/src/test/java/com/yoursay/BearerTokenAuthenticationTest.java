package com.yoursay;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.when;

@QuarkusTest
public class BearerTokenAuthenticationTest {

//    KeycloakTestClient keycloakClient = new KeycloakTestClient();

//    @Test
//    public void testAdminAccess() {
//        RestAssured.given().auth().oauth2(getAccessToken("admin"))
//                .when().get("/api/admin")
//                .then()
//                .statusCode(200);
//
//        RestAssured.given().auth().oauth2(getAccessToken("alice"))
//                .when().get("/api/admin")
//                .then()
//                .statusCode(403);
//    }

//    @Test
//    public void testUserAccess() {
//
//        given().auth().oauth2(getAccessToken("alice"))
//                .when().get("/gateway/not-a-service/no-url")
//                .then()
//                .statusCode(200);
//
////        RestAssured.given().auth().oauth2(getAccessToken("admin"))
////                .when().get("/api/users/me")
////                .then()
////                .statusCode(200);
//    }
//
//    protected String getAccessToken(String userName) {
//        return keycloakClient.getAccessToken(userName);
//    }

    @InjectMock
    RoutingClient routingClient;

    @Test
    public void testGetRouterTokenPropagation() {
        // Generate a JWT token using the SmallRye JWT builder with the necessary claims.
        // The token is signed and includes our issuer and other claims.
        String token = Jwt.issuer("https://issuer.example.com")  // Set the expected issuer
                .upn("max")                            // Set the user principal name
                .claim("sub", "max123")                // Subject claim
                .claim("email_verified", "max@example.com") // Email claim
                .claim("given_name", "Max")            // Given name claim
                .claim("family_name", "Java")          // Family name claim
                .sign();                               // Sign the token to produce a JWT string

        // Configure the mock RoutingClient to return "success" when called with the correct token.
        // Note: The controller adds "Bearer " in front of the token before forwarding it.
        Mockito.when(routingClient.routeGet("Bearer " + token, "service1", "someUrl"))
                .thenReturn(Uni.createFrom().item("success"));

        // Use RestAssured to send an HTTP GET request to the API gateway endpoint.
        // The generated token is provided in the Authorization header.
        given()
                .header("Authorization", "Bearer " + token) // Attach the JWT in the header
                .when()
                .get("/gateway/service1/someUrl")            // Call the endpoint with service and URL parameters
                .then()
                .statusCode(200)                             // Verify that the response status is 200 OK
                .body(is("success"));                        // Verify that the response body is "success"
    }


}

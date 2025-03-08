//package com.yoursay;
//
//import io.quarkus.test.keycloak.client.KeycloakTestClient;
//import io.smallrye.jwt.build.Jwt;
//
//import java.util.HashMap;
//import java.util.Map;
//
//public class CustomKeycloakClient extends KeycloakTestClient {
//
//    @Override
//    public String getAccessToken(String sub, String fName, String lName, String email){
//
//        Map<String, Object> claims = new HashMap<>();
//
//        claims.put("sub", sub);
//        claims.put("fName", fName);
//        claims.put("lName", lName);
//        claims.put("email", email);
//
//        return Jwt.claims(claims)
//                .issuer("http://localhost:63663/realms/quarkus")
//                .subject(sub)
//                .preferredUserName(fName)
//                .audience("")
//                .expiresIn(3600)
//                .sign();
//
//    }
//}

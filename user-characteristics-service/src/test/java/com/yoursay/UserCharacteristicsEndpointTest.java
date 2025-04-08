package com.yoursay;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class UserCharacteristicsEndpointTest {

    private int testUserCharacteristicUserId = 1001;


    // todo build tests for user characteristics endpoint

    @Test
    public void testGetUserCharacteristicsEndpoint(){
        given()
                .when()
                .get("/user-characteristics/" + testUserCharacteristicUserId)
                .then()
                .statusCode(200);
    }

    @Test
    public void testGetUserCharacteristicsEndpoint_ReturnsCharacteristics(){
        // Send a GET request to the endpoint with the test user characteristic userId
        given()
                .when()
                .get("/user-characteristics/" + testUserCharacteristicUserId)
                .then()
                .statusCode(200)
                .body("userId", equalTo(testUserCharacteristicUserId))
                .body("postcode", equalTo("AB12CD"))
                .body("ukCounty", equalTo("GREATER_MANCHESTER"))
                .body("raceEnum", equalTo("WHITE"))
                .body("incomeRangeEnum", equalTo("BETWEEN_50K_AND_100K"))
                .body("countryOfBirthEnum", equalTo("UNITED_KINGDOM"))
                .body("politicalPersuasionEnum", equalTo("LEFT"))
                .body("sexAtBirthEnum", equalTo("MALE"))
                .body("heightEnum", equalTo("FEET_6_1_TO_6_3"))
                .body("eyeColorEnum", equalTo("BLUE"))
                .body("weightRangeEnum", equalTo("KG_70_79"))
                .body("parentEnum", equalTo("MUM"))
                .body("universityEducated", equalTo(true))
                .body("universitySubjectEnum", equalTo("ENGINEERING"))
                .body("propertyOwner", equalTo(true));
    }

}

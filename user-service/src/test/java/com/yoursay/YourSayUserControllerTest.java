package com.yoursay;

import com.yoursay.user.model.UserRole;
import com.yoursay.user.model.YourSayUser;
import com.yoursay.user.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class YourSayUserControllerTest {

    private String baseUrl = "/your-say-user";

    private YourSayUser existingUser;
    private YourSayUser existingForDeleteUser;

    @Inject
    YourSayUserRepository yourSayUserRepository;



}
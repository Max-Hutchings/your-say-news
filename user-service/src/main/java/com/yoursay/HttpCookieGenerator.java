package com.yoursay;

import com.yoursay.model.YourSayUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.NewCookie;
import java.util.Calendar;
import java.util.Date;

@ApplicationScoped
public class HttpCookieGenerator {

    private static final String HTTP_ONLY_COOKIE_NAME = "YourSayUserId";
    private static final int EXPIRY_DAYS = 7; // cookie lifespan in days

    public NewCookie generateNewAuthCookie(YourSayUser user) {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, EXPIRY_DAYS);
        Date expiryDate = cal.getTime();


        return new NewCookie.Builder(HTTP_ONLY_COOKIE_NAME)
                .value(user.getId().toString())
                .path("/")     // Makes the cookie valid for the entire app
                .expiry(expiryDate)
                .httpOnly(true)
                .secure(false)
                .build();
    }
}

package com.yoursay.user;

import com.yoursay.user.model.YourSayUser;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.NewCookie;
import java.util.Calendar;
import java.util.Date;

@ApplicationScoped
public class HttpCookieGenerator {

    public static final String HTTP_ONLY_COOKIE_NAME = "YourSayUserId";
    public static final int EXPIRY_DAYS = 7; // cookie lifespan in days

    public NewCookie generateNewAuthCookie(YourSayUser user) {

        if (user.getId() == null){
            Log.error("User passed in to cookie generator without valid id present");
            return null;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, EXPIRY_DAYS);
        Date expiryDate = cal.getTime();


        return new NewCookie.Builder(HTTP_ONLY_COOKIE_NAME)
                .value(user.getId().toString())
//                .path("/")     // Makes the cookie valid for the entire app
                .expiry(expiryDate)
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.NONE)
                .build();
    }
}

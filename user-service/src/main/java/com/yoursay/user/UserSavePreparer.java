package com.yoursay.user;

import com.yoursay.user.model.YourSayUser;
import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;

@ApplicationScoped
public class UserSavePreparer {

    public void prepareUserForSave(YourSayUser yourSayUser) {
        encryptAttachedPassword(yourSayUser);
        yourSayUser.setCreatedDate(LocalDate.now());
        yourSayUser.setActive(true);
    }

    private void encryptAttachedPassword(YourSayUser yourSayUser) {
        String rawPassword = yourSayUser.getPassword();
        String salt = BCrypt.gensalt();
        String bcryptHash = BCrypt.hashpw(rawPassword, salt);
        yourSayUser.setPassword(bcryptHash);
    }
}

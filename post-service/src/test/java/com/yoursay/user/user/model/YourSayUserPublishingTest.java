package com.yoursay.user.user.model;

import com.yoursay.user.user.AccountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Publishing is its own permission (ADR-066): on by default for official accounts, off by default
 * for admins, never for users. Changing account type resets it to the new type's default.
 */
class YourSayUserPublishingTest {

    @Test
    void newAccountIsAUserWhoCanNeverPublish() {
        YourSayUser account = new YourSayUser("reader@yoursay.example", "Rosa", "Reader");

        assertEquals(AccountType.USER, account.getAccountType());
        assertFalse(account.isPublishingEnabled());
        assertFalse(account.canPublish());
        assertThrows(IllegalArgumentException.class, () -> account.setPublishingEnabled(true));
        assertFalse(account.isPublishingEnabled());
    }

    @Test
    void officialAccountsPublishByDefaultAndCanBeSwitchedOff() {
        YourSayUser account = new YourSayUser("editor@yoursay.example", "Editorial", "Desk");

        account.setAccountType(AccountType.OFFICIAL);
        assertTrue(account.isPublishingEnabled());
        assertTrue(account.canPublish());

        account.setPublishingEnabled(false);
        assertFalse(account.canPublish());
    }

    @Test
    void adminsStartWithoutPublishingAndCanBeGrantedIt() {
        YourSayUser account = new YourSayUser("ops@yoursay.example", "Olu", "Admin");

        account.setAccountType(AccountType.ADMIN);
        assertFalse(account.isPublishingEnabled());
        assertFalse(account.canPublish());

        account.setPublishingEnabled(true);
        assertTrue(account.canPublish());
    }

    @Test
    void anInactiveAccountCannotPublishEvenWithPublishingEnabled() {
        YourSayUser account = new YourSayUser("editor@yoursay.example", "Editorial", "Desk");
        account.setAccountType(AccountType.OFFICIAL);

        account.setActive(false);
        assertTrue(account.isPublishingEnabled());
        assertFalse(account.canPublish());

        account.setActive(true);
        assertTrue(account.canPublish());
    }

    @Test
    void changingTypeResetsPublishingToTheNewTypesDefault() {
        YourSayUser account = new YourSayUser("moving@yoursay.example", "Morgan", "Mover");
        account.setAccountType(AccountType.ADMIN);
        account.setPublishingEnabled(true);

        account.setAccountType(AccountType.OFFICIAL);
        assertTrue(account.isPublishingEnabled());
        account.setPublishingEnabled(false);

        account.setAccountType(AccountType.ADMIN);
        assertFalse(account.isPublishingEnabled());
        account.setPublishingEnabled(true);

        account.setAccountType(AccountType.USER);
        assertFalse(account.isPublishingEnabled());
    }

    @Test
    void reassigningTheSameTypeKeepsAnAdminChosenSetting() {
        YourSayUser admin = new YourSayUser("ops@yoursay.example", "Olu", "Admin");
        admin.setAccountType(AccountType.ADMIN);
        admin.setPublishingEnabled(true);
        YourSayUser official = new YourSayUser("editor@yoursay.example", "Editorial", "Desk");
        official.setAccountType(AccountType.OFFICIAL);
        official.setPublishingEnabled(false);

        admin.setAccountType(AccountType.ADMIN);
        official.setAccountType(AccountType.OFFICIAL);

        assertTrue(admin.isPublishingEnabled());
        assertFalse(official.isPublishingEnabled());
    }
}

package com.yoursay.user.user.model;

import com.yoursay.user.user.AccountType;
import com.yoursay.user.user.PublisherStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YourSayUserPublishingTest {

    @Test
    void onlyAnActiveOfficialCanPublish() {
        YourSayUser account = new YourSayUser("editor@yoursay.example", "Editorial", "Desk");

        assertEquals(AccountType.STANDARD, account.getAccountType());
        assertEquals(PublisherStatus.NONE, account.getPublisherStatus());
        assertFalse(account.canPublish());

        account.setAccountType(AccountType.OFFICIAL);
        assertFalse(account.canPublish());

        account.setPublisherStatus(PublisherStatus.ACTIVE);
        assertTrue(account.canPublish());

        account.setActive(false);
        assertFalse(account.canPublish());

        account.setActive(true);
        assertTrue(account.canPublish());

        account.setPublisherStatus(PublisherStatus.SUSPENDED);
        assertFalse(account.canPublish());
    }

    @Test
    void returningToStandardClearsPublishingStateAndRejectsPublisherStatuses() {
        YourSayUser account = new YourSayUser("former.editor@yoursay.example", "Former", "Editor");
        account.setAccountType(AccountType.OFFICIAL);
        account.setPublisherStatus(PublisherStatus.ACTIVE);

        account.setAccountType(AccountType.STANDARD);

        assertEquals(PublisherStatus.NONE, account.getPublisherStatus());
        assertFalse(account.canPublish());
        assertThrows(IllegalArgumentException.class,
                () -> account.setPublisherStatus(PublisherStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> account.setPublisherStatus(PublisherStatus.SUSPENDED));
    }
}

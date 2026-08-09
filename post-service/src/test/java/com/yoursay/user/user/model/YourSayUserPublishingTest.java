package com.yoursay.user.user.model;

import com.yoursay.user.user.AccountType;
import com.yoursay.user.user.PublisherStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YourSayUserPublishingTest {

    @Test
    void onlyAnActiveOfficialCanPublish() {
        YourSayUser account = new YourSayUser("editor@yoursay.example", "Editorial", "Desk");

        assertEquals(AccountType.USER, account.getAccountType());
        assertEquals(PublisherStatus.NONE, account.getPublisherStatus());
        assertFalse(account.canPublish());

        account.setAccountType(AccountType.OFFICIAL);
        assertEquals(PublisherStatus.ACTIVE, account.getPublisherStatus());
        assertTrue(account.canPublish());

        account.setActive(false);
        assertFalse(account.canPublish());

        account.setActive(true);
        assertTrue(account.canPublish());

        account.setPublisherStatus(PublisherStatus.SUSPENDED);
        assertFalse(account.canPublish());

        account.setPublisherStatus(PublisherStatus.NONE);
        assertFalse(account.canPublish());
    }

    @Test
    void nonOfficialAccountTypesClearPublishingStateAndRejectPublisherStatuses() {
        YourSayUser account = new YourSayUser("former.editor@yoursay.example", "Former", "Editor");
        account.setAccountType(AccountType.OFFICIAL);

        account.setAccountType(AccountType.USER);

        assertEquals(PublisherStatus.NONE, account.getPublisherStatus());
        assertFalse(account.canPublish());
        assertThrows(IllegalArgumentException.class,
                () -> account.setPublisherStatus(PublisherStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> account.setPublisherStatus(PublisherStatus.SUSPENDED));

        account.setAccountType(AccountType.OFFICIAL);
        account.setAccountType(AccountType.ADMIN);

        assertEquals(PublisherStatus.NONE, account.getPublisherStatus());
        assertFalse(account.canPublish());
        assertThrows(IllegalArgumentException.class,
                () -> account.setPublisherStatus(PublisherStatus.ACTIVE));
    }
}

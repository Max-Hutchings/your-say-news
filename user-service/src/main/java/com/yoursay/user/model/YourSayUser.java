package com.yoursay.user.model;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;


import java.time.Instant;
import java.time.LocalDate;


@Entity
@Table(name="your_say_user")
public class YourSayUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "handle", nullable = false, unique = true, length = 40)
    private String handle;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "date_of_birth" )
    private LocalDate dateOfBirth;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDate createdDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * When the user gave explicit consent to the privacy promise, and the policy version they
     * agreed to. Null until they consent. Lives here, with identity/PII — never with the
     * characteristic data it governs.
     */
    @Column(name = "consented_at")
    private Instant consentedAt;

    @Column(name = "privacy_policy_version")
    private String privacyPolicyVersion;

    public YourSayUser() {
    }

    /** Record explicit consent to the given privacy-policy version, stamped now. */
    public void recordConsent(String policyVersion) {
        this.consentedAt = Instant.now();
        this.privacyPolicyVersion = policyVersion;
    }

    @PrePersist
    protected  void onCreate(){
        if (createdDate == null){
            createdDate = LocalDate.now();
        }
        ensurePublicProfile();
    }

    @PreUpdate
    protected void onUpdate() {
        ensurePublicProfile();
    }

    public YourSayUser(String email, String firstName, String lastName) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        ensurePublicProfile();
    }

    public YourSayUser(String email, LocalDate dateOfBirth, String firstName, String lastName) {
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdDate = LocalDate.now();
        ensurePublicProfile();

    }

    public YourSayUser(Long id, String email, String firstName, String lastName, LocalDate dateOfBirth, LocalDate createdDate, boolean active) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.createdDate = createdDate;
        this.active = active;
        ensurePublicProfile();
    }

    private void ensurePublicProfile() {
        if (displayName == null || displayName.isBlank()) {
            displayName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        }
        if (handle == null || handle.isBlank()) {
            String source = displayName == null || displayName.isBlank() ? email : displayName;
            handle = source == null ? "user" : source.toLowerCase().replaceAll("[^a-z0-9_.]+", ".");
            handle = handle.replaceAll("^\\.+|\\.+$", "");
            if (handle.isBlank()) {
                handle = "user";
            }
            if (handle.length() > 40) {
                handle = handle.substring(0, 40);
            }
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getfirstName() {
        return firstName;
    }

    public void setfirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getlastName() {
        return lastName;
    }

    public void setlastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getConsentedAt() {
        return consentedAt;
    }

    public void setConsentedAt(Instant consentedAt) {
        this.consentedAt = consentedAt;
    }

    public String getPrivacyPolicyVersion() {
        return privacyPolicyVersion;
    }

    public void setPrivacyPolicyVersion(String privacyPolicyVersion) {
        this.privacyPolicyVersion = privacyPolicyVersion;
    }
}

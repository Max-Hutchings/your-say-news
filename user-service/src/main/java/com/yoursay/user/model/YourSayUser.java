package com.yoursay.user.model;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;


import java.time.LocalDate;


@Entity
@Table(name="your_say_user")
public class YourSayUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "f_name", nullable = false)
    private String fName;

    @Column(name = "l_name", nullable = false)
    private String lName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "created_date", nullable = false)
    private LocalDate createdDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    public YourSayUser() {
    }

    ;

    public YourSayUser(String email, LocalDate dateOfBirth, String fName, String lName) {
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.fName = fName;
        this.lName = lName;
        this.createdDate = LocalDate.now();

    }

    public YourSayUser(Long id, String email, String fName, String lName, LocalDate dateOfBirth, LocalDate createdDate, boolean active) {
        this.id = id;
        this.email = email;
        this.fName = fName;
        this.lName = lName;
        this.dateOfBirth = dateOfBirth;
        this.createdDate = createdDate;
        this.active = active;
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

    public String getfName() {
        return fName;
    }

    public void setfName(String fName) {
        this.fName = fName;
    }

    public String getlName() {
        return lName;
    }

    public void setlName(String lName) {
        this.lName = lName;
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
}


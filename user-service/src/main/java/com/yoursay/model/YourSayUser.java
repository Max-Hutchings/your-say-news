package com.yoursay.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.*;


import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name="your_say_user")
public class YourSayUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name="email", nullable = false, unique = true)
    private String email;

    @Column(name="password", nullable = true, unique = false)
    private String password;

    @Column(name="username", nullable = false, unique = true)
    private String username;

    @Column(name="f_name", nullable = false)
    private String fName;

    @Column(name="l_name", nullable = false)
    private String lName;

    @Column(name="date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name="created_date", nullable = false)
    private LocalDate createdDate;

    @Column(name="active", nullable = false)
    private boolean active;

    @Column(name="role")
    @Enumerated(EnumType.STRING)
    public UserRole role;

    public YourSayUser(){}


    public YourSayUser(String email, String username, String fName, String lName, LocalDate dateOfBirth) {
        this.email = email;
        this.username = username;
        this.fName = fName;
        this.lName = lName;
        this.dateOfBirth = dateOfBirth;
    }

    public YourSayUser(Long id, String email, String username, String fName, String lName, LocalDate dateOfBirth, LocalDate createdDate, UserRole role) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.fName = fName;
        this.lName = lName;
        this.dateOfBirth = dateOfBirth;
        this.createdDate = createdDate;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "YourSayUser{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", username='" + username + '\'' +
                ", fName='" + fName + '\'' +
                ", lName='" + lName + '\'' +
                ", dateOfBirth=" + dateOfBirth +
                ", createdDate=" + createdDate +
                ", active=" + active +
                ", role=" + role +
                '}';
    }
}

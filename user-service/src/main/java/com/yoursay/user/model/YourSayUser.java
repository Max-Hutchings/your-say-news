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

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth" )
    private LocalDate dateOfBirth;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDate createdDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public YourSayUser() {
    }

    @PrePersist
    protected  void onCreate(){
        if (createdDate == null){
            createdDate = LocalDate.now();
        }
    }

    public YourSayUser(String email, String firstName, String lastName) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public YourSayUser(String email, LocalDate dateOfBirth, String firstName, String lastName) {
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdDate = LocalDate.now();

    }

    public YourSayUser(Long id, String email, String firstName, String lastName, LocalDate dateOfBirth, LocalDate createdDate, boolean active) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
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


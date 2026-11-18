package com.sessionscheduler.scheduler.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterForm {
    @NotBlank(message = "Enter your full name.")
    @Size(max = 100, message = "Keep the name under 100 characters.")
    private String fullName;

    @NotBlank(message = "Enter your email.")
    @Email(message = "Enter a full email address, such as name@example.com.")
    @Size(max = 190, message = "That email is too long.")
    private String email;

    @NotBlank(message = "Choose a password.")
    @Size(min = 8, max = 72, message = "Use 8 to 72 characters.")
    private String password;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

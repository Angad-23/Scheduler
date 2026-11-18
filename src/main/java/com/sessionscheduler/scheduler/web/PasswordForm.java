package com.sessionscheduler.scheduler.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

public class PasswordForm implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Enter your current password.")
    private String currentPassword;

    @NotBlank(message = "Choose a new password.")
    @Size(min = 8, max = 72, message = "Use 8 to 72 characters.")
    private String newPassword;

    @NotBlank(message = "Type the new password again.")
    private String confirmPassword;

    @AssertTrue(message = "The two new passwords don't match.")
    public boolean isConfirmed() {
        return newPassword == null || confirmPassword == null || Objects.equals(newPassword, confirmPassword);
    }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}

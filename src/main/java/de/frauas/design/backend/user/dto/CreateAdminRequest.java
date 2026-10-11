package de.frauas.design.backend.user.dto;

import de.frauas.design.backend.shared.util.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request payload for creating an administrator account. */
@Data
public class CreateAdminRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @Email
    @NotBlank
    @Size(max = 255)
    private String email;

    @NotBlank
    @Pattern(
            regexp = PasswordPolicy.PASSWORD_REGEX,
            message = PasswordPolicy.MESSAGE)
    private String password;
}

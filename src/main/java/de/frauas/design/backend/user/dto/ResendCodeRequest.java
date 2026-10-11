package de.frauas.design.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code POST /user/resend_code}.
 */
@Data
public class ResendCodeRequest {

    @Email
    @NotBlank
    @Size(max = 255)
    private String email;
}

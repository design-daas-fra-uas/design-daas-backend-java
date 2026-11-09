package de.frauas.design.backend.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for POST /endpoint. {@code functionName} must match the blueprint name that
 * design-daas sends to {@code GET /permissions/{functionName}/{userId}}.
 */
@Data
public class EndpointCreateRequest {

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "[\\w.-]+", message = "may only contain letters, digits, '_', '.' and '-'")
    private String functionName;

    @Size(max = 255)
    private String description;
}

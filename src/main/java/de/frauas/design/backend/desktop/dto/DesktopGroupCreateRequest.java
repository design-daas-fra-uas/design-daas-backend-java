package de.frauas.design.backend.desktop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for POST /desktop_group.
 * "name" is optional — falls back to "description" when blank.
 */
@Data
public class DesktopGroupCreateRequest {

    /** Optional — falls back to description when blank. */
    @Size(max = 255)
    private String name;

    /** Required */
    @NotBlank
    @Size(max = 255)
    private String description;
}

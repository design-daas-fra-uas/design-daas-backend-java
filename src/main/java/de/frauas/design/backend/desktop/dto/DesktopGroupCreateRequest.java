package de.frauas.design.backend.desktop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request DTO for POST /desktop_group. */
@Data
public class DesktopGroupCreateRequest {

    @NotBlank
    @Size(max = 255)
    private String description;
}

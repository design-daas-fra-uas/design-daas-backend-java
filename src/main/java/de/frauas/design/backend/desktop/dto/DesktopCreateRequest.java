package de.frauas.design.backend.desktop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for POST /desktop.
 * The Frontend sends { "description": "...", "groups": [{ "description": "..." }] }.
 * Each subgroup is named after its description.
 */
@Data
public class DesktopCreateRequest {

    /** Required */
    @NotBlank
    @Size(max = 255)
    private String description;

    /** Optional — cascade-create desktop subgroups on creation. */
    private List<@Valid SubGroupRequest> groups;

    /** A desktop subgroup to be created alongside the parent desktop. */
    @Data
    public static class SubGroupRequest {
        @NotBlank
        @Size(max = 255)
        private String description;
    }
}

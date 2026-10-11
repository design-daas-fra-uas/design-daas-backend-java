package de.frauas.design.backend.desktop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for POST /desktop.
 * The Frontend sends { "description": "...", "groups": [{ "description": "..." }] }.
 * "name" is optional — falls back to "description" when absent.
 */
@Data
public class DesktopCreateRequest {

    /** Optional — falls back to description when not provided. */
    @Size(max = 255)
    private String name;

    /** Required */
    @NotBlank
    @Size(max = 255)
    private String description;

    /** Optional — cascade-create desktop subgroups on creation. */
    private List<@Valid SubGroupRequest> groups;

    /** A desktop subgroup to be created alongside the parent desktop. */
    @Data
    public static class SubGroupRequest {
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 255)
        private String description;
    }
}

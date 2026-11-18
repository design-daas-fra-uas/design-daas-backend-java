package de.frauas.design.backend.permission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for PUT /endpoints/sync: the complete list of names design-daas checks with
 * {@code GET /permissions/{functionName}/{userId}}, i.e. its route names and typed
 * permissions (e.g. {@code baseimage_create_vm}).
 */
@Data
public class EndpointSyncRequest {

    @NotNull
    private List<@Valid Item> endpoints;

    /** One route of design-daas. */
    @Data
    public static class Item {

        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "[\\w.-]+", message = "may only contain letters, digits, '_', '.' and '-'")
        private String functionName;

        // Longer descriptions are cut to the column size by the service
        private String description;
    }
}

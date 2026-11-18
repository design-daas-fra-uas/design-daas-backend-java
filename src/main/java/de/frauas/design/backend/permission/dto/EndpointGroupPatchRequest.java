package de.frauas.design.backend.permission.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Partial-update request for PATCH /endpoint_group/{id}.
 * All fields are optional — a {@code null} value means "do not change".
 *
 * <p>A non-null {@code endpointIds} replaces the group's endpoints entirely.</p>
 */
@Data
public class EndpointGroupPatchRequest {

    @Size(min = 1, max = 255, message = "name must be between 1 and 255 characters")
    private String name;

    @Size(max = 255)
    private String description;

    private List<Integer> endpointIds;
}

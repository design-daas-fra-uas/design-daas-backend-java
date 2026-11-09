package de.frauas.design.backend.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Request DTO for POST /endpoint_group. {@code endpointIds} may be omitted for an empty group. */
@Data
public class EndpointGroupCreateRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String description;

    private List<Integer> endpointIds;
}

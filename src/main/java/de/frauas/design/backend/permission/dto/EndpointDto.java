package de.frauas.design.backend.permission.dto;

import de.frauas.design.backend.permission.model.Endpoint;
import lombok.Builder;
import lombok.Data;

/** Response payload for endpoint endpoints: one design-daas function that rules can target. */
@Data
@Builder
public class EndpointDto {
    private Integer id;
    private String functionName;
    private String description;

    /** Maps an {@link Endpoint} entity to its API representation. */
    public static EndpointDto from(Endpoint endpoint) {
        return EndpointDto.builder()
                .id(endpoint.getId())
                .functionName(endpoint.getFunctionName())
                .description(endpoint.getDescription())
                .build();
    }
}

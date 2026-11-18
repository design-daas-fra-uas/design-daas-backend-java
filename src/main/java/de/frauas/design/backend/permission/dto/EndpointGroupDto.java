package de.frauas.design.backend.permission.dto;

import de.frauas.design.backend.permission.model.Endpoint;
import de.frauas.design.backend.permission.model.EndpointGroup;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Response payload for endpoint-group endpoints: a named set of endpoints one rule can target. */
@Data
@Builder
public class EndpointGroupDto {
    private Integer id;
    private String name;
    private String description;
    private List<Integer> endpointIds;

    /** Maps an {@link EndpointGroup} entity to its API representation. */
    public static EndpointGroupDto from(EndpointGroup group) {
        return EndpointGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .endpointIds(group.getEndpoints().stream().map(Endpoint::getId).toList())
                .build();
    }
}

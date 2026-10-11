package de.frauas.design.backend.desktop.dto;

import de.frauas.design.backend.desktop.model.Desktop;
import de.frauas.design.backend.desktop.model.DesktopGroup;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request/response payload for {@code DesktopGroup} CRUD endpoints.
 *
 * <p>Note: {@code name} is intentionally not annotated with bean-validation constraints
 * because {@code DesktopGroupController#createDesktopGroup} allows a blank name to fall
 * back to {@code description}. Emptiness of both fields together is rejected by
 * {@link de.frauas.design.backend.desktop.service.DesktopService}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesktopGroupDto {
    private Integer id;
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String description;
    private List<Integer> desktopIds;

    /** Maps a {@link DesktopGroup} entity to its API representation. */
    public static DesktopGroupDto from(DesktopGroup g) {
        return DesktopGroupDto.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .desktopIds(g.getDesktops().stream().map(Desktop::getId).toList())
                .build();
    }
}

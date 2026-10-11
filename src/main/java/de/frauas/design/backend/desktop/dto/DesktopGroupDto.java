package de.frauas.design.backend.desktop.dto;

import de.frauas.design.backend.desktop.model.Desktop;
import de.frauas.design.backend.desktop.model.DesktopGroup;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response payload for {@code DesktopGroup} read endpoints. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesktopGroupDto {
    private Integer id;

    @Size(max = 255)
    private String description;

    private List<Integer> desktopIds;

    /** Maps a {@link DesktopGroup} entity to its API representation. */
    public static DesktopGroupDto from(DesktopGroup g) {
        return DesktopGroupDto.builder()
                .id(g.getId())
                .description(g.getDescription())
                .desktopIds(g.getDesktops().stream().map(Desktop::getId).toList())
                .build();
    }
}

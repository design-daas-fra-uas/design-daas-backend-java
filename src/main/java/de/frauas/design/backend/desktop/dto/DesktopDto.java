package de.frauas.design.backend.desktop.dto;

import de.frauas.design.backend.desktop.model.Desktop;
import de.frauas.design.backend.desktop.model.DesktopGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload for {@code Desktop} read endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesktopDto {
    private Integer id;
    private String description;

    /**
     * IDs of DesktopGroups this desktop belongs to.
     */
    private List<Integer> groups;

    /**
     * Maps a {@link Desktop} entity to its API representation.
     */
    public static DesktopDto from(Desktop d) {
        return DesktopDto.builder()
                .id(d.getId())
                .description(d.getDescription())
                .groups(d.getDesktopGroups().stream().map(DesktopGroup::getId).toList())
                .build();
    }
}

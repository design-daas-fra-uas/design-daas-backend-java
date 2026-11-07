package de.frauas.design.backend.user.dto;

import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.model.UserGroup;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Response payload for regular-user endpoints; {@code groups} holds the user's group IDs. */
@Data
@Builder
public class UserDto {
    private Integer id;
    private String name;
    private String email;
    private String role;
    private List<Integer> groups;
    private boolean enabled;

    /** Maps a {@link User} entity to its API representation, exposing its group IDs. */
    public static UserDto from(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .groups(user.getGroups().stream().map(UserGroup::getId).toList())
                .enabled(user.isEnabled())
                .build();
    }
}

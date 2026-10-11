package de.frauas.design.backend.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.frauas.design.backend.user.model.UserGroup;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request/response payload for {@code UserGroup} CRUD endpoints.
 *
 * <p>Note: {@code name} is intentionally not annotated with bean-validation constraints
 * because {@code UserGroupController#createUserGroup} allows a blank name to fall back
 * to {@code description}. Emptiness of both fields together is rejected by
 * {@link de.frauas.design.backend.user.service.UserGroupService}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupDto {
    private Integer id;
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String description;

    /**
     * Optional — only used in PATCH /user_group/{id} request body.
     * Full replacement of group members: supply the complete list of user IDs.
     * Omit (or set to null) to leave existing members unchanged.
     * Not included in response bodies.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Integer> userIds;

    /** Maps a {@link UserGroup} entity to its API representation (member IDs omitted). */
    public static UserGroupDto from(UserGroup group) {
        return UserGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                // userIds intentionally omitted from response
                .build();
    }
}

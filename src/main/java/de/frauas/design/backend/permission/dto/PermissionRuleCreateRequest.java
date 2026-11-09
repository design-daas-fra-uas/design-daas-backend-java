package de.frauas.design.backend.permission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for POST /permission_rule. {@code subjectId} and {@code targetId} are
 * interpreted according to {@code kind} (see {@link PermissionRuleKind}).
 */
@Data
public class PermissionRuleCreateRequest {

    @NotNull
    private PermissionRuleKind kind;

    @NotNull
    private Integer subjectId;

    @NotNull
    private Integer targetId;

    @NotNull
    private Boolean allow;
}

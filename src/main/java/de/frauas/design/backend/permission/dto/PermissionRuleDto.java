package de.frauas.design.backend.permission.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response payload for one permission rule.
 *
 * <p>{@code subjectId} is a user id or a user-group id and {@code targetId} an endpoint id
 * or an endpoint-group id, depending on {@code kind}. {@code id} is only unique within one
 * kind, which is why deleting a rule needs both. {@code targetName} is the function or
 * endpoint-group name, so clients do not need a second lookup.</p>
 */
@Data
@Builder
public class PermissionRuleDto {
    private Integer id;
    private PermissionRuleKind kind;
    private Integer subjectId;
    private Integer targetId;
    private String targetName;
    private boolean allow;
}

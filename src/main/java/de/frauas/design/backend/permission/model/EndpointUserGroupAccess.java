package de.frauas.design.backend.permission.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Permission rule granting or denying a user group access to one endpoint
 * (resolution step 2).
 */
@Entity
@Table(
        name = "endpoint_user_group_access",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_endpoint_user_group_access",
                columnNames = {"user_group_id", "endpoint_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EndpointUserGroupAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_group_id", nullable = false)
    private Integer userGroupId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private Endpoint endpoint;

    @Column(name = "allow_access", nullable = false)
    private boolean allowAccess;
}

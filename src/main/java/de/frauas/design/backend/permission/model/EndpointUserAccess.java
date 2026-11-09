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
 * Direct permission rule for one user and one endpoint (resolution step 1, most specific).
 */
@Entity
@Table(
        name = "endpoint_user_access",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_endpoint_user_access",
                columnNames = {"user_id", "endpoint_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EndpointUserAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private Endpoint endpoint;

    @Column(name = "allow_access", nullable = false)
    private boolean allowAccess;
}

package de.frauas.design.backend.permission.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single callable design-daas function that permission rules can target directly
 * or indirectly through an {@link EndpointGroup}. The list is not edited by hand: design-daas
 * sends it at startup (see {@code PermissionManagementService#syncEndpoints}).
 */
@Entity
@Table(name = "endpoints")
@Getter
@Setter
@NoArgsConstructor
public class Endpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "function_name", nullable = false, unique = true)
    private String functionName;

    @Column
    private String description;
}

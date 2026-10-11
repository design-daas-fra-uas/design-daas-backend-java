package de.frauas.design.backend.desktop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A named group of {@link Desktop}s. Access is granted collectively by associating a
 * {@link de.frauas.design.backend.user.model.UserGroup} with a {@code DesktopGroup}.
 */
@Entity
@Table(name = "desktop_groups")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"desktops", "userGroups"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DesktopGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(nullable = false, unique = true)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "desktops_to_desktop_groups",
            joinColumns = @JoinColumn(name = "desktop_group_id"),
            inverseJoinColumns = @JoinColumn(name = "desktop_id"))
    private List<Desktop> desktops = new ArrayList<>();

    @ManyToMany(mappedBy = "desktopGroups", fetch = FetchType.LAZY)
    private List<de.frauas.design.backend.user.model.UserGroup> userGroups = new ArrayList<>();
}

package de.frauas.design.backend.desktop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A single virtual desktop, optionally organised into one or more {@link DesktopGroup}s.
 */
@Entity
@Table(name = "desktops")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "desktopGroups")
public class Desktop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column
    private String description;

    /** Inverse side of DesktopGroup.desktops — allows DesktopDto to include group IDs. */
    @ManyToMany(mappedBy = "desktops", fetch = FetchType.LAZY)
    private List<DesktopGroup> desktopGroups = new ArrayList<>();
}

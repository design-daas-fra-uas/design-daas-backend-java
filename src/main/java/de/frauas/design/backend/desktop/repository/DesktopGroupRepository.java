package de.frauas.design.backend.desktop.repository;

import de.frauas.design.backend.desktop.model.DesktopGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link DesktopGroup} persistence. */
@Repository
public interface DesktopGroupRepository extends JpaRepository<DesktopGroup, Integer> {

    boolean existsByName(String name);
}

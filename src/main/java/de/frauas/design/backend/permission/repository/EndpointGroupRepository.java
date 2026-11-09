package de.frauas.design.backend.permission.repository;

import de.frauas.design.backend.permission.model.EndpointGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for named endpoint groups.
 */
@Repository
public interface EndpointGroupRepository extends JpaRepository<EndpointGroup, Integer> {

    /** Returns whether an endpoint group with this name already exists. */
    boolean existsByName(String name);

    /** Returns every endpoint group that contains the given endpoint. */
    List<EndpointGroup> findAllByEndpoints_Id(Integer endpointId);
}

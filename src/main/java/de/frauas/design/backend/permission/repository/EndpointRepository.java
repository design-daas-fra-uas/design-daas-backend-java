package de.frauas.design.backend.permission.repository;

import de.frauas.design.backend.permission.model.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for individual callable design-daas functions.
 */
@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Integer> {

    /** Finds an endpoint by its unique function name. */
    Optional<Endpoint> findByFunctionName(String functionName);

    /** Returns whether an endpoint with this function name already exists. */
    boolean existsByFunctionName(String functionName);
}

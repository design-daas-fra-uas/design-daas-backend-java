package de.frauas.design.backend.permission.repository;

import de.frauas.design.backend.permission.model.EndpointUserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for direct per-user, per-endpoint permission rules (resolution step 1).
 */
@Repository
public interface EndpointUserAccessRepository extends JpaRepository<EndpointUserAccess, Integer> {

    /**
     * Returns every direct user rule matching the target function, ordered by creation order.
     *
     * <p>The schema does not enforce uniqueness on {@code (user_id, endpoint_id)}, so callers must
     * be prepared to resolve multiple equally-specific rows deterministically.</p>
     */
    List<EndpointUserAccess> findAllByUserIdAndEndpoint_FunctionNameOrderByIdAsc(Integer userId, String functionName);

    /** Returns whether a rule for this user and endpoint already exists. */
    boolean existsByUserIdAndEndpoint_Id(Integer userId, Integer endpointId);

    /** Deletes every rule targeting the given endpoint. */
    void deleteAllByEndpoint_Id(Integer endpointId);
}

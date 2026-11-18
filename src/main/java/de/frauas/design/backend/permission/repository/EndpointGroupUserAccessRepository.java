package de.frauas.design.backend.permission.repository;

import de.frauas.design.backend.permission.model.EndpointGroupUserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for per-user, per-endpoint-group permission rules (resolution step 3).
 */
@Repository
public interface EndpointGroupUserAccessRepository extends JpaRepository<EndpointGroupUserAccess, Integer> {

    /**
     * Returns every user endpoint-group rule whose endpoint group contains the target function.
     *
     * <p>Filtering is done in the query so callers don't need to load every rule/endpoint-group for
     * the user and filter in memory.</p>
     */
    List<EndpointGroupUserAccess> findAllByUserIdAndEndpointGroup_Endpoints_FunctionNameOrderByIdAsc(
            Integer userId, String functionName);

    /** Returns whether a rule for this user and endpoint group already exists. */
    boolean existsByUserIdAndEndpointGroup_Id(Integer userId, Integer endpointGroupId);

    /** Deletes every rule targeting the given endpoint group. */
    void deleteAllByEndpointGroup_Id(Integer endpointGroupId);
}

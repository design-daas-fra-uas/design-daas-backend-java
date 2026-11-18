package de.frauas.design.backend.permission.repository;

import de.frauas.design.backend.permission.model.EndpointGroupUserGroupAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for per-user-group, per-endpoint-group permission rules (resolution step 4).
 */
@Repository
public interface EndpointGroupUserGroupAccessRepository extends JpaRepository<EndpointGroupUserGroupAccess, Integer> {

    /**
     * Returns every user-group endpoint-group rule whose endpoint group contains the target function.
     *
     * <p>Filtering is done in the query so callers don't need to load every rule/endpoint-group for
     * the user's groups and filter in memory.</p>
     */
    List<EndpointGroupUserGroupAccess> findAllByUserGroupIdInAndEndpointGroup_Endpoints_FunctionNameOrderByIdAsc(
            List<Integer> userGroupIds, String functionName);

    /** Returns whether a rule for this user group and endpoint group already exists. */
    boolean existsByUserGroupIdAndEndpointGroup_Id(Integer userGroupId, Integer endpointGroupId);

    /** Deletes every rule targeting the given endpoint group. */
    void deleteAllByEndpointGroup_Id(Integer endpointGroupId);
}

package de.frauas.design.backend.permission.repository;

import de.frauas.design.backend.permission.model.EndpointUserGroupAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for per-user-group, per-endpoint permission rules (resolution step 2).
 */
@Repository
public interface EndpointUserGroupAccessRepository extends JpaRepository<EndpointUserGroupAccess, Integer> {

    /**
     * Returns every matching group rule for the target function, ordered by creation order.
     *
     * <p>A user may belong to multiple groups and the schema does not enforce uniqueness, so callers
     * must resolve conflicting matches explicitly instead of relying on database row order.</p>
     */
    List<EndpointUserGroupAccess> findAllByUserGroupIdInAndEndpoint_FunctionNameOrderByIdAsc(
            List<Integer> userGroupIds, String functionName);

    /** Returns whether a rule for this user group and endpoint already exists. */
    boolean existsByUserGroupIdAndEndpoint_Id(Integer userGroupId, Integer endpointId);

    /** Deletes every rule targeting the given endpoint. */
    void deleteAllByEndpoint_Id(Integer endpointId);
}

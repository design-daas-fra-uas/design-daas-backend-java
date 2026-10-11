package de.frauas.design.backend.auth.repository;

import de.frauas.design.backend.auth.model.AccessTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence access for issued JWT access tokens and their revocation metadata.
 */
@Repository
public interface AccessTokenRepository extends JpaRepository<AccessTokenEntity, Long> {

    /**
     * Find by JWT ID claim — used for revocation checks in TokenRevocationValidator / SessionService.
     */
    Optional<AccessTokenEntity> findByJti(String jti);

    @Modifying
    @Transactional
    @Query("DELETE FROM AccessTokenEntity at WHERE at.userId = :userId")
    void deleteByUserId(@Param("userId") Integer userId);

    /**
     * Deletes all access tokens whose {@code expiresAt} is strictly before {@code now}.
     *
     * @return the number of rows deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AccessTokenEntity at WHERE at.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /**
     * Revokes every not-yet-revoked token of the given user.
     *
     * @return the number of rows updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE AccessTokenEntity at SET at.revoked = true, at.revokedAt = :now "
            + "WHERE at.userId = :userId AND at.revoked = false")
    int revokeAllByUserId(@Param("userId") Integer userId, @Param("now") Instant now);
}

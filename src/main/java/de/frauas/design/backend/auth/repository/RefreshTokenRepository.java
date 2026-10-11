package de.frauas.design.backend.auth.repository;

import de.frauas.design.backend.auth.model.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence access for issued opaque refresh tokens and their revocation state.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    /**
     * Returns the token only if it has not been revoked — the primary grant lookup.
     *
     * <p>The pessimistic write lock prevents two concurrent refresh requests from
     * consuming the same token at the same time before one of them can mark it revoked.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshTokenEntity rt WHERE rt.tokenValue = :tokenValue AND rt.revoked = false")
    Optional<RefreshTokenEntity> findActiveByTokenValue(@Param("tokenValue") String tokenValue);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.userId = :userId")
    void deleteByUserId(@Param("userId") Integer userId);

    /**
     * Deletes all refresh tokens whose {@code expiresAt} is strictly before {@code now}.
     *
     * @return the number of rows deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /**
     * Revokes every not-yet-revoked token of the given user.
     *
     * @return the number of rows updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenEntity rt SET rt.revoked = true, rt.revokedAt = :now "
            + "WHERE rt.userId = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") Integer userId, @Param("now") Instant now);
}

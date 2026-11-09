package de.frauas.design.backend.user.repository;

import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data-access for {@link BaseUser} and its subtypes ({@link User}, {@link Admin}).
 * Uses single-table inheritance, so most queries filter by concrete
 * subtype via JPQL.
 */
@Repository
public interface UserRepository extends JpaRepository<BaseUser, Integer> {

    /** Finds any account (user or admin) by its unique email address. */
    Optional<BaseUser> findByEmail(String email);

    /** Returns all regular users ordered by ID. */
    @Query("SELECT u FROM User u ORDER BY u.id")
    List<User> findAllUsers();

    /** Returns all admin accounts ordered by ID. */
    @Query("SELECT a FROM Admin a ORDER BY a.id")
    List<Admin> findAllAdmins();

    /** Finds an admin by ID, excluding regular users. */
    @Query("SELECT a FROM Admin a WHERE a.id = :id")
    Optional<Admin> findAdminById(@Param("id") Integer id);

    /** Counts all admin accounts. */
    @Query("SELECT COUNT(a) FROM Admin a")
    long countAdmins();

    /** Finds a regular user by ID, excluding admins. */
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findUserById(@Param("id") Integer id);

    /** Used by PermissionService to fetch the user's group IDs. */
    @Query("SELECT ug.id FROM User u JOIN u.groups ug WHERE u.id = :userId")
    List<Integer> findGroupIdsByUserId(@Param("userId") Integer userId);

    /** Returns whether any account (user or admin) already uses this e-mail address. */
    boolean existsByEmail(String email);
}

package de.frauas.design.backend.user.controller;

import de.frauas.design.backend.auth.service.JwtUserResolver;
import de.frauas.design.backend.shared.security.Authorities;
import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.dto.AdminDto;
import de.frauas.design.backend.user.dto.CreateAdminRequest;
import de.frauas.design.backend.user.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for admin CRUD. All endpoints require the {@code SCOPE_admin}
 * authority (enforced class-wide).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize(Authorities.IS_ADMIN)
public class AdminController {

    private final AdminService adminService;
    private final JwtUserResolver jwtUserResolver;

    /**
     * Lists all admin accounts.
     *
     * @return 200 OK with every {@link AdminDto} known to the system
     */
    @GetMapping("/admins")
    public ResponseEntity<List<AdminDto>> getAllAdmins() {
        log.info("GET /admins — listing all admins");
        List<AdminDto> admins = adminService.getAllAdmins();
        log.debug("GET /admins — returning {} admins", admins.size());
        return ResponseEntity.ok(admins);
    }

    /**
     * Looks up a single admin account.
     *
     * @param adminId the ID of the admin
     * @return 200 OK with the matching {@link AdminDto}, or 404 if no such admin exists
     */
    @GetMapping("/admin/{adminId}")
    public ResponseEntity<AdminDto> getAdminById(@PathVariable Integer adminId) {
        log.info("GET /admin/{} — fetching admin", adminId);
        return ResponseEntity.ok(adminService.getAdminById(adminId));
    }

    /**
     * Creates a new admin account.
     *
     * @param request the new admin's details
     * @return 201 Created with no body
     */
    @PostMapping("/admin")
    public ResponseEntity<Void> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        log.info("POST /admin — creating admin email={}", LogMasking.maskEmail(request.getEmail()));
        AdminDto result = adminService.createAdmin(request);
        log.info("POST /admin — admin created id={} email={}", result.getId(), LogMasking.maskEmail(result.getEmail()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Permanently deletes another admin account and e-mails them about it.
     *
     * @param adminId the ID of the admin to delete
     * @param jwt the caller's JWT, used to stop admins from deleting themselves
     * @return 204 No Content; 404 if no such admin; 409 if it is the caller or the last admin
     */
    @DeleteMapping("/admin/{adminId}")
    public ResponseEntity<Void> deleteAdmin(@PathVariable Integer adminId, @AuthenticationPrincipal Jwt jwt) {
        Integer callerId = jwtUserResolver.resolveFromAuthenticatedJwt(jwt);
        log.info("DELETE /admin/{} — deleting admin callerId={}", adminId, callerId);
        adminService.deleteAdmin(adminId, callerId);
        log.info("DELETE /admin/{} — admin deleted", adminId);
        return ResponseEntity.noContent().build();
    }
}

package de.frauas.design.backend.user.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
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
}

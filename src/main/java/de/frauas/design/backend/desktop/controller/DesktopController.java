package de.frauas.design.backend.desktop.controller;

import de.frauas.design.backend.desktop.dto.DesktopCreateRequest;
import de.frauas.design.backend.desktop.dto.DesktopDto;
import de.frauas.design.backend.desktop.exception.DesktopNotFoundException;
import de.frauas.design.backend.desktop.service.DesktopService;
import de.frauas.design.backend.shared.security.Authorities;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for desktop CRUD. All endpoints require the {@code SCOPE_admin}
 * authority (enforced class-wide).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize(Authorities.IS_ADMIN)
public class DesktopController {

    private final DesktopService desktopService;

    /**
     * Lists all desktops.
     *
     * @return 200 OK with every {@link DesktopDto} known to the system
     */
    @GetMapping("/desktops")
    public ResponseEntity<List<DesktopDto>> getAllDesktops() {

        log.info("GET /desktops — listing all desktops");

        List<DesktopDto> desktops = desktopService.getAllDesktops();

        log.info("GET /desktops — returning {} desktops", desktops.size());

        return ResponseEntity.ok(desktops);
    }

    /**
     * Creates a new desktop, optionally cascade-creating desktop subgroups for it.
     * Business rules (e.g. each subgroup being named after its description) are enforced
     * by {@link DesktopService#createDesktop}, not here.
     *
     * @param request the desktop to create
     * @return 201 Created with no body
     */
    @PostMapping("/desktop")
    public ResponseEntity<Void> createDesktop(@Valid @RequestBody DesktopCreateRequest request) {

        log.info("POST /desktop — creating desktop description={}", request.getDescription());

        desktopService.createDesktop(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Looks up a single desktop by ID.
     *
     * @param id the desktop ID
     * @return 200 OK with the matching {@link DesktopDto}
     * @throws DesktopNotFoundException if no desktop exists with the given id
     */
    @GetMapping("/desktop/{id}")
    public ResponseEntity<DesktopDto> getDesktopById(@PathVariable Integer id) {

        log.info("GET /desktop/{} — fetching desktop", id);

        return ResponseEntity.ok(desktopService.getDesktopById(id));
    }
}

package de.frauas.design.backend.user.service;

import de.frauas.design.backend.shared.util.AfterCommit;
import de.frauas.design.backend.shared.util.LogMasking;
import de.frauas.design.backend.user.dto.AdminDto;
import de.frauas.design.backend.user.dto.CreateAdminRequest;
import de.frauas.design.backend.user.exception.AdminDeletionNotAllowedException;
import de.frauas.design.backend.user.exception.AdminNotFoundException;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Core business logic for admin account management.
 *
 * <p>Unlike regular users ({@link UserService}), admins are enabled
 * immediately and are not subject to email verification.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountValidator accountValidator;
    private final MailService mailService;

    /**
     * Returns every admin account.
     *
     * @return all admins
     */
    @Transactional(readOnly = true)
    public List<AdminDto> getAllAdmins() {
        log.debug("getAllAdmins");
        return userRepository.findAllAdmins().stream().map(AdminDto::from).toList();
    }

    /**
     * Looks up a single admin by ID.
     *
     * @param id the ID of the admin
     * @return the matching admin
     * @throws AdminNotFoundException if no admin exists with the given id
     */
    @Transactional(readOnly = true)
    public AdminDto getAdminById(Integer id) {
        log.debug("getAdminById — id={}", id);
        return AdminDto.from(findAdminById(id));
    }

    /**
     * Creates a new admin account. Unlike regular users, admins are enabled immediately
     * and are not subject to email verification.
     *
     * @param request the new admin's details
     * @return the created admin
     * @throws de.frauas.design.backend.user.exception.WeakPasswordException if the password fails policy checks
     * @throws de.frauas.design.backend.user.exception.EmailAlreadyInUseException if the email is already registered
     */
    @Transactional
    public AdminDto createAdmin(CreateAdminRequest request) {
        log.debug("createAdmin — email={}", LogMasking.maskEmail(request.getEmail()));
        accountValidator.validatePassword(request.getPassword());
        accountValidator.assertEmailAvailable(request.getEmail(), null);
        Admin admin = new Admin();
        admin.setName(request.getName());
        admin.setEmail(request.getEmail());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setEnabled(true);
        userRepository.save(admin);
        log.info("createAdmin — admin created id={} email={}", admin.getId(), LogMasking.maskEmail(admin.getEmail()));
        return AdminDto.from(admin);
    }

    /**
     * Permanently deletes an admin account and e-mails them that it is gone.
     *
     * <p>Two rules keep the system manageable: an admin cannot delete their own account
     * (so nobody locks themselves out by mistake), and the last remaining admin cannot be
     * deleted. Tokens are removed by the {@code ON DELETE CASCADE} foreign keys.</p>
     *
     * @param id the ID of the admin to delete
     * @param callerId the ID of the admin performing the deletion (from the JWT subject)
     * @throws AdminNotFoundException if no admin exists with the given id
     * @throws AdminDeletionNotAllowedException if {@code id} is the caller or the last admin
     */
    @Transactional
    public void deleteAdmin(Integer id, Integer callerId) {
        log.debug("deleteAdmin — id={} callerId={}", id, callerId);
        Admin admin = findAdminById(id);
        if (id.equals(callerId)) {
            log.warn("deleteAdmin — rejected: admin id={} tried to delete themself", id);
            throw new AdminDeletionNotAllowedException("Admins cannot delete their own account");
        }
        if (userRepository.countAdmins() <= 1) {
            log.warn("deleteAdmin — rejected: id={} is the last admin", id);
            throw new AdminDeletionNotAllowedException("The last admin cannot be deleted");
        }
        String email = admin.getEmail();
        String name = admin.getName();
        userRepository.delete(admin);
        AfterCommit.run(() -> mailService.sendAccountDeletedEmail(email, name));
        log.info("deleteAdmin — admin deleted id={} email={}", id, LogMasking.maskEmail(email));
    }

    private Admin findAdminById(Integer id) {
        return userRepository.findAdminById(id).orElseThrow(() -> {
            log.warn("findAdminById — adminId={} not found", id);
            return new AdminNotFoundException(id);
        });
    }
}

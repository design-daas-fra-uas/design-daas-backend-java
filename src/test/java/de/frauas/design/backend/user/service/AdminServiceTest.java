package de.frauas.design.backend.user.service;

import de.frauas.design.backend.user.dto.AdminDto;
import de.frauas.design.backend.user.dto.CreateAdminRequest;
import de.frauas.design.backend.user.exception.AdminDeletionNotAllowedException;
import de.frauas.design.backend.user.exception.AdminNotFoundException;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService")
class AdminServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    MailService mailService;

    AdminService adminService;

    @BeforeEach
    void setUp() {
        AccountValidator accountValidator = new AccountValidator(userRepository);
        adminService = new AdminService(userRepository, passwordEncoder, accountValidator, mailService);
    }

    private Admin makeAdmin(Integer id, String email) {
        Admin a = new Admin();
        a.setId(id);
        a.setName("Admin User");
        a.setEmail(email);
        a.setPassword("encoded");
        a.setEnabled(true);
        return a;
    }

    @Nested
    @DisplayName("createAdmin")
    class CreateAdmin {

        @Test
        @DisplayName("creates admin with valid data")
        void createAdmin_valid_returnsDto() {
            CreateAdminRequest req = new CreateAdminRequest();
            req.setName("AdminX");
            req.setEmail("admin@example.com");
            req.setPassword("AdminPass1");

            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("AdminPass1")).thenReturn("hashed");
            when(userRepository.save(any())).thenAnswer(inv -> {
                Admin a = inv.getArgument(0);
                a.setId(5);
                return a;
            });

            AdminDto dto = adminService.createAdmin(req);

            assertThat(dto.getEmail()).isEqualTo("admin@example.com");
            assertThat(dto.getRole()).isEqualTo("admin");
        }

        @Test
        @DisplayName("throws on duplicate admin email")
        void createAdmin_duplicateEmail_throws() {
            CreateAdminRequest req = new CreateAdminRequest();
            req.setName("AdminX");
            req.setEmail("admin@example.com");
            req.setPassword("AdminPass1");

            when(userRepository.findByEmail("admin@example.com"))
                    .thenReturn(Optional.of(makeAdmin(1, "admin@example.com")));

            assertThatThrownBy(() -> adminService.createAdmin(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already in use");
        }
    }

    @Nested
    @DisplayName("getAllAdmins")
    class GetAllAdmins {

        @Test
        @DisplayName("returns all admins as DTOs")
        void getAllAdmins_returnsAllAsDtos() {
            when(userRepository.findAllAdmins())
                    .thenReturn(List.of(makeAdmin(1, "a1@e.com"), makeAdmin(2, "a2@e.com")));

            List<AdminDto> admins = adminService.getAllAdmins();

            assertThat(admins).hasSize(2);
            assertThat(admins).extracting(AdminDto::getEmail).containsExactlyInAnyOrder("a1@e.com", "a2@e.com");
        }
    }

    @Nested
    @DisplayName("getAdminById")
    class GetAdminById {

        @Test
        @DisplayName("returns the admin")
        void getAdminById_existing_returnsDto() {
            when(userRepository.findAdminById(2)).thenReturn(Optional.of(makeAdmin(2, "a@example.com")));

            assertThat(adminService.getAdminById(2).getEmail()).isEqualTo("a@example.com");
        }

        @Test
        @DisplayName("throws when no admin has that id")
        void getAdminById_notFound_throws() {
            when(userRepository.findAdminById(9)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getAdminById(9)).isInstanceOf(AdminNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteAdmin")
    class DeleteAdmin {

        @Test
        @DisplayName("deletes another admin and e-mails them")
        void deleteAdmin_otherAdmin_deletesAndMails() {
            Admin target = makeAdmin(2, "target@example.com");
            when(userRepository.findAdminById(2)).thenReturn(Optional.of(target));
            when(userRepository.countAdmins()).thenReturn(2L);

            adminService.deleteAdmin(2, 1);

            verify(userRepository).delete(target);
            verify(mailService).sendAccountDeletedEmail("target@example.com", "Admin User");
        }

        @Test
        @DisplayName("refuses to delete the caller's own account")
        void deleteAdmin_self_throws() {
            when(userRepository.findAdminById(1)).thenReturn(Optional.of(makeAdmin(1, "me@example.com")));

            assertThatThrownBy(() -> adminService.deleteAdmin(1, 1))
                    .isInstanceOf(AdminDeletionNotAllowedException.class);
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("refuses to delete the last admin")
        void deleteAdmin_lastAdmin_throws() {
            Admin only = makeAdmin(2, "only@example.com");
            when(userRepository.findAdminById(2)).thenReturn(Optional.of(only));
            when(userRepository.countAdmins()).thenReturn(1L);

            assertThatThrownBy(() -> adminService.deleteAdmin(2, 1))
                    .isInstanceOf(AdminDeletionNotAllowedException.class);
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws 404-mapped exception for unknown id or a regular user")
        void deleteAdmin_unknown_throws() {
            when(userRepository.findAdminById(9)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.deleteAdmin(9, 1)).isInstanceOf(AdminNotFoundException.class);
        }
    }
}

package de.frauas.design.backend.user.service;

import de.frauas.design.backend.user.dto.CreateUserRequest;
import de.frauas.design.backend.user.dto.PatchUserRequest;
import de.frauas.design.backend.user.dto.UserDto;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.model.UserGroup;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserGroupRepository userGroupRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    MailService mailService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    UserService userService;

    @BeforeEach
    void setUp() {
        // AccountValidator is a real (non-mocked) collaborator backed by the mocked
        // repository — its behaviour is simple enough not to need its own mock here,
        // and using the real thing keeps these tests exercising the actual validation rules.
        AccountValidator accountValidator = new AccountValidator(userRepository);
        userService =
                new UserService(userRepository, userGroupRepository, passwordEncoder, mailService, accountValidator, eventPublisher);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User makeUser(Integer id, String email, boolean enabled) {
        User u = new User();
        u.setId(id);
        u.setName("Test User");
        u.setEmail(email);
        u.setPassword("encoded");
        u.setEnabled(enabled);
        u.setRegistrationCode("ABCD1234");
        u.setRegistrationCodeTimeout(LocalDateTime.now().plusHours(1));
        return u;
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

    // -------------------------------------------------------------------------
    // createUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("creates user with valid data")
        void createUser_validData_savesAndReturnsDto() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Alice");
            req.setEmail("alice@example.com");
            req.setPassword("Password1");

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Password1")).thenReturn("hashed");
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1);
                return u;
            });

            UserDto dto = userService.createUser(req);

            assertThat(dto.getEmail()).isEqualTo("alice@example.com");
            assertThat(dto.getName()).isEqualTo("Alice");
            assertThat(dto.isEnabled()).isFalse();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("throws when email already in use")
        void createUser_duplicateEmail_throwsIllegalArgument() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Alice");
            req.setEmail("alice@example.com");
            req.setPassword("Password1");

            when(userRepository.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(makeUser(1, "alice@example.com", true)));

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already in use");
        }

        @Test
        @DisplayName("throws when password is too short")
        void createUser_weakPassword_throwsIllegalArgument() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Alice");
            req.setEmail("alice@example.com");
            req.setPassword("weak");

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Password must be at least 8");
        }

        @Test
        @DisplayName("throws when password has no uppercase")
        void createUser_noUppercase_throwsIllegalArgument() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Alice");
            req.setEmail("alice@example.com");
            req.setPassword("password1");

            assertThatThrownBy(() -> userService.createUser(req)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when password has no digit")
        void createUser_noDigit_throwsIllegalArgument() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Alice");
            req.setEmail("alice@example.com");
            req.setPassword("PasswordOnly");

            assertThatThrownBy(() -> userService.createUser(req)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("assigns groups when group IDs are provided")
        void createUser_withGroups_assignsGroups() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Alice");
            req.setEmail("alice@example.com");
            req.setPassword("Password1");
            req.setGroups(List.of(10, 20));

            UserGroup g1 = new UserGroup();
            g1.setId(10);
            g1.setName("G1");
            UserGroup g2 = new UserGroup();
            g2.setId(20);
            g2.setName("G2");

            when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userGroupRepository.findAllById(new LinkedHashSet<>(List.of(10, 20))))
                    .thenReturn(List.of(g1, g2));
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1);
                return u;
            });

            UserDto dto = userService.createUser(req);

            assertThat(dto.getGroups()).containsExactlyInAnyOrder(10, 20);
        }

        @Test
        @DisplayName("rejects unknown group IDs instead of silently dropping them")
        void createUser_unknownGroups_throws() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Alice");
            req.setEmail("alice@example.com");
            req.setPassword("Password1");
            req.setGroups(List.of(10, 20));

            UserGroup g1 = new UserGroup();
            g1.setId(10);
            g1.setName("G1");

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Password1")).thenReturn("hashed");
            when(userGroupRepository.findAllById(new LinkedHashSet<>(List.of(10, 20))))
                    .thenReturn(List.of(g1));

            assertThatThrownBy(() -> userService.createUser(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown user group ids: [20]");
            verify(userRepository, never()).save(any());
            verifyNoInteractions(mailService);
        }
    }

    // -------------------------------------------------------------------------
    // validateEmail
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("validateEmail")
    class ValidateEmail {

        @Test
        @DisplayName("returns true and enables user on valid code")
        void validateEmail_validCode_enablesUser() {
            User u = makeUser(1, "alice@example.com", false);

            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(u));

            boolean result = userService.validateEmail("alice@example.com", "ABCD1234");

            assertThat(result).isTrue();
            assertThat(u.isEnabled()).isTrue();
            assertThat(u.getRegistrationCode()).isNull();
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("returns false when code is wrong")
        void validateEmail_wrongCode_returnsFalse() {
            User u = makeUser(1, "alice@example.com", false);
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(u));

            boolean result = userService.validateEmail("alice@example.com", "WRONGCOD");

            assertThat(result).isFalse();
            assertThat(u.isEnabled()).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("returns false when code is expired")
        void validateEmail_expiredCode_returnsFalse() {
            User u = makeUser(1, "alice@example.com", false);
            u.setRegistrationCodeTimeout(LocalDateTime.now().minusMinutes(1));
            when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(u));

            boolean result = userService.validateEmail("alice@example.com", "ABCD1234");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when user not found")
        void validateEmail_userNotFound_returnsFalse() {
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            boolean result = userService.validateEmail("missing@example.com", "ABCD1234");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when found entity is Admin, not User")
        void validateEmail_adminFound_returnsFalse() {
            Admin a = makeAdmin(2, "admin@example.com");
            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(a));

            boolean result = userService.validateEmail("admin@example.com", "ABCD1234");

            assertThat(result).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // getAllUsers
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsers {

        @Test
        @DisplayName("returns every user, unpaginated")
        void getAllUsers_returnsAllUsers() {
            List<User> allUsers = List.of(
                    makeUser(1, "u1@e.com", true), makeUser(2, "u2@e.com", true), makeUser(3, "u3@e.com", true));
            when(userRepository.findAllUsers()).thenReturn(allUsers);

            List<UserDto> result = userService.getAllUsers();

            assertThat(result).hasSize(3);
            assertThat(result.getFirst().getEmail()).isEqualTo("u1@e.com");
        }

        @Test
        @DisplayName("returns empty list when there are no users")
        void getAllUsers_noUsers_returnsEmpty() {
            when(userRepository.findAllUsers()).thenReturn(List.of());

            List<UserDto> result = userService.getAllUsers();

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // updateUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("updates name when provided")
        void updateUser_newName_updatesName() {
            User u = makeUser(1, "u@e.com", true);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userRepository.save(any())).thenReturn(u);

            PatchUserRequest req = new PatchUserRequest();
            req.setName("New Name");

            UserDto dto = userService.updateUser(1, req);

            assertThat(dto.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("updates password with encoding when provided")
        void updateUser_newPassword_encodesAndUpdates() {
            User u = makeUser(1, "u@e.com", true);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(passwordEncoder.encode("NewPass1")).thenReturn("hashed2");
            when(userRepository.save(any())).thenReturn(u);

            PatchUserRequest req = new PatchUserRequest();
            req.setPassword("NewPass1");

            userService.updateUser(1, req);

            assertThat(u.getPassword()).isEqualTo("hashed2");
        }

        @Test
        @DisplayName("throws on weak new password")
        void updateUser_weakPassword_throws() {
            User u = makeUser(1, "u@e.com", true);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));

            PatchUserRequest req = new PatchUserRequest();
            req.setPassword("weak");

            assertThatThrownBy(() -> userService.updateUser(1, req)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when user not found")
        void updateUser_notFound_throws() {
            when(userRepository.findUserById(99)).thenReturn(Optional.empty());

            PatchUserRequest req = new PatchUserRequest();
            req.setName("X");

            assertThatThrownBy(() -> userService.updateUser(99, req)).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("throws when new email is already used by another user")
        void updateUser_emailInUseByAnotherUser_throws() {
            User u = makeUser(1, "u@e.com", true);
            User other = makeUser(2, "taken@e.com", true);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userRepository.findByEmail("taken@e.com")).thenReturn(Optional.of(other));

            PatchUserRequest req = new PatchUserRequest();
            req.setEmail("taken@e.com");

            assertThatThrownBy(() -> userService.updateUser(1, req)).isInstanceOf(IllegalArgumentException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("allows updating to the user's own current email")
        void updateUser_sameEmail_noConflict() {
            User u = makeUser(1, "u@e.com", true);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userRepository.save(any())).thenReturn(u);

            PatchUserRequest req = new PatchUserRequest();
            req.setEmail("u@e.com");

            UserDto dto = userService.updateUser(1, req);

            assertThat(dto.getEmail()).isEqualTo("u@e.com");
            verify(userRepository, never()).findByEmail(anyString());
        }

        @Test
        @DisplayName("updates to a new, unused email")
        void updateUser_newUnusedEmail_updates() {
            User u = makeUser(1, "u@e.com", true);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userRepository.findByEmail("new@e.com")).thenReturn(Optional.empty());
            when(userRepository.save(any())).thenReturn(u);

            PatchUserRequest req = new PatchUserRequest();
            req.setEmail("new@e.com");

            UserDto dto = userService.updateUser(1, req);

            assertThat(dto.getEmail()).isEqualTo("new@e.com");
        }

        @Test
        @DisplayName("replaces groups when explicit group IDs are provided")
        void updateUser_groupsProvided_replacesGroups() {
            User u = makeUser(1, "u@e.com", true);
            UserGroup g1 = new UserGroup();
            g1.setId(10);
            g1.setName("G1");
            UserGroup g2 = new UserGroup();
            g2.setId(20);
            g2.setName("G2");
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userGroupRepository.findAllById(new LinkedHashSet<>(List.of(10, 20))))
                    .thenReturn(List.of(g1, g2));
            when(userRepository.save(any())).thenReturn(u);

            PatchUserRequest req = new PatchUserRequest();
            req.setGroups(List.of(10, 20));

            UserDto dto = userService.updateUser(1, req);

            assertThat(dto.getGroups()).containsExactlyInAnyOrder(10, 20);
        }

        @Test
        @DisplayName("rejects unknown group IDs during update")
        void updateUser_unknownGroups_throws() {
            User u = makeUser(1, "u@e.com", true);
            UserGroup g1 = new UserGroup();
            g1.setId(10);
            g1.setName("G1");
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userGroupRepository.findAllById(new LinkedHashSet<>(List.of(10, 20))))
                    .thenReturn(List.of(g1));

            PatchUserRequest req = new PatchUserRequest();
            req.setGroups(List.of(10, 20));

            assertThatThrownBy(() -> userService.updateUser(1, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown user group ids: [20]");
            verify(userRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // enableUser / disableUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("enableUser / disableUser")
    class EnableDisableUser {

        @Test
        @DisplayName("enableUser sets enabled=true")
        void enableUser_setsEnabled() {
            User u = makeUser(1, "u@e.com", false);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userRepository.save(any())).thenReturn(u);

            userService.enableUser(1);

            assertThat(u.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("disableUser sets enabled=false")
        void disableUser_setsDisabled() {
            User u = makeUser(1, "u@e.com", true);
            when(userRepository.findUserById(1)).thenReturn(Optional.of(u));
            when(userRepository.save(any())).thenReturn(u);

            userService.disableUser(1);

            assertThat(u.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enableUser throws when the user does not exist")
        void enableUser_notFound_throws() {
            when(userRepository.findUserById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.enableUser(99)).isInstanceOf(NoSuchElementException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("disableUser throws when the user does not exist")
        void disableUser_notFound_throws() {
            when(userRepository.findUserById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.disableUser(99)).isInstanceOf(NoSuchElementException.class);
            verify(userRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // password validation boundary tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("password validation")
    class PasswordValidation {

        /** Tries to create a user with the given password using lenient mocks (success path). */
        private void tryCreate(String password) {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("X");
            req.setEmail("x@x.com");
            req.setPassword(password);
            lenient().when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
            lenient().when(passwordEncoder.encode(any())).thenReturn("h");
            lenient().when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1);
                return u;
            });
            userService.createUser(req);
        }

        @Test
        @DisplayName("accepts exactly 8-char password with upper, lower, digit")
        void password_exactly8chars_accepted() {
            assertThatCode(() -> tryCreate("Abc1defg")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects null password")
        void password_null_rejected() {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("X");
            req.setEmail("x@x.com");
            req.setPassword(null);

            assertThatThrownBy(() -> userService.createUser(req)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects 7-char password")
        void password_7chars_rejected() {
            // Call service directly — password check happens before any repository call
            CreateUserRequest req = new CreateUserRequest();
            req.setName("X");
            req.setEmail("x@x.com");
            req.setPassword("Abc1def"); // exactly 7 chars, valid pattern but too short

            assertThatThrownBy(() -> userService.createUser(req)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------------------------
    // requestApplication
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("requestApplication")
    class RequestApplication {

        @Test
        @DisplayName("sends application-request email for the requesting user")
        void requestApplication_validUser_sendsEmail() {
            User u = makeUser(1, "u@e.com", true);
            u.setName("Alice");
            when(userRepository.findById(1)).thenReturn(Optional.of(u));

            userService.requestApplication(1, "some-app");

            verify(mailService).sendApplicationRequestEmail("u@e.com", "Alice", "1", "some-app");
        }

        @Test
        @DisplayName("throws NoSuchElementException when user not found")
        void requestApplication_userNotFound_throws() {
            when(userRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.requestApplication(99, "some-app"))
                    .isInstanceOf(NoSuchElementException.class);
            verifyNoInteractions(mailService);
        }
    }
}

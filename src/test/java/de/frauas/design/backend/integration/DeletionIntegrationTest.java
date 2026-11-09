package de.frauas.design.backend.integration;

import de.frauas.design.backend.desktop.model.Desktop;
import de.frauas.design.backend.desktop.model.DesktopGroup;
import de.frauas.design.backend.desktop.repository.DesktopGroupRepository;
import de.frauas.design.backend.desktop.repository.DesktopRepository;
import de.frauas.design.backend.user.model.Admin;
import de.frauas.design.backend.user.model.BaseUser;
import de.frauas.design.backend.user.model.User;
import de.frauas.design.backend.user.model.UserGroup;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static de.frauas.design.backend.integration.MockJwt.adminJwt;
import static de.frauas.design.backend.integration.MockJwt.userJwt;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code DELETE /user/{id}}, {@code DELETE /admin/{id}},
 * {@code DELETE /user_group/{id}} and {@code DELETE /desktop_group/{id}}. All of them are admin-only.
 */
@DisplayName("Delete endpoints Integration")
class DeletionIntegrationTest extends BaseIntegrationTest {

    private static final String USER_EMAIL = "delete.user@example.com";
    private static final String ADMIN_A_EMAIL = "delete.admin.a@example.com";
    private static final String ADMIN_B_EMAIL = "delete.admin.b@example.com";
    private static final String GROUP_NAME = "delete-test-group";
    private static final String DESKTOP_GROUP = "delete-test-desktop-group";
    private static final String DESKTOP = "delete-test-desktop";

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserGroupRepository userGroupRepository;

    @Autowired
    DesktopRepository desktopRepository;

    @Autowired
    DesktopGroupRepository desktopGroupRepository;

    @BeforeEach
    void cleanup() {
        for (String email : List.of(USER_EMAIL, ADMIN_A_EMAIL, ADMIN_B_EMAIL)) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        // The user group owns its desktop-group links, so deleting it first also removes those.
        userGroupRepository.findByName(GROUP_NAME).ifPresent(userGroupRepository::delete);
        desktopGroupRepository.findAll().stream()
                .filter(g -> DESKTOP_GROUP.equals(g.getDescription()))
                .forEach(desktopGroupRepository::delete);
        desktopRepository.findAll().stream()
                .filter(d -> DESKTOP.equals(d.getDescription()))
                .forEach(desktopRepository::delete);
    }

    private <T extends BaseUser> T save(T account, String email) {
        account.setName("Delete Test");
        account.setEmail(email);
        account.setPassword("encoded");
        account.setEnabled(true);
        return userRepository.save(account);
    }

    @Test
    @DisplayName("DELETE /user/{id} – admin deletes the user, second call returns 404")
    void deleteUser_admin_returns204ThenNotFound() throws Exception {
        User user = save(new User(), USER_EMAIL);

        mockMvc.perform(delete("/user/" + user.getId()).with(adminJwt(999))).andExpect(status().isNoContent());
        assertFalse(userRepository.existsById(user.getId()));

        mockMvc.perform(delete("/user/" + user.getId()).with(adminJwt(999))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /user/{id} – user JWT returns 403, no JWT returns 401")
    void deleteUser_nonAdmin_isRejected() throws Exception {
        User user = save(new User(), USER_EMAIL);

        mockMvc.perform(delete("/user/" + user.getId()).with(userJwt(user.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/user/" + user.getId())).andExpect(status().isUnauthorized());
        assertTrue(userRepository.existsById(user.getId()));
    }

    @Test
    @DisplayName("DELETE /user/{id} – an admin id is not a user and returns 404")
    void deleteUser_adminId_returns404() throws Exception {
        Admin admin = save(new Admin(), ADMIN_A_EMAIL);

        mockMvc.perform(delete("/user/" + admin.getId()).with(adminJwt(999))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /admin/{id} – deletes another admin, refuses to delete the caller")
    void deleteAdmin_otherAndSelf() throws Exception {
        Admin caller = save(new Admin(), ADMIN_A_EMAIL);
        Admin other = save(new Admin(), ADMIN_B_EMAIL);

        mockMvc.perform(delete("/admin/" + caller.getId()).with(adminJwt(caller.getId())))
                .andExpect(status().isConflict());
        assertTrue(userRepository.existsById(caller.getId()));

        mockMvc.perform(delete("/admin/" + other.getId()).with(adminJwt(caller.getId())))
                .andExpect(status().isNoContent());
        assertFalse(userRepository.existsById(other.getId()));
    }

    @Test
    @DisplayName("DELETE /admin/{id} – user JWT returns 403")
    void deleteAdmin_userJwt_returns403() throws Exception {
        Admin admin = save(new Admin(), ADMIN_A_EMAIL);

        mockMvc.perform(delete("/admin/" + admin.getId()).with(userJwt(1))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /user_group/{id} – deletes the group and keeps its members")
    void deleteUserGroup_keepsMembers() throws Exception {
        UserGroup group = new UserGroup();
        group.setName(GROUP_NAME);
        group = userGroupRepository.save(group);
        User member = new User();
        member.getGroups().add(group);
        member = save(member, USER_EMAIL);

        mockMvc.perform(delete("/user_group/" + group.getId()).with(adminJwt(999)))
                .andExpect(status().isNoContent());

        assertFalse(userGroupRepository.existsById(group.getId()));
        assertTrue(userRepository.existsById(member.getId()));
        assertTrue(userRepository.findGroupIdsByUserId(member.getId()).isEmpty());

        mockMvc.perform(delete("/user_group/" + group.getId()).with(adminJwt(999)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /desktop_group/{id} – deletes the group and keeps its desktop and user group")
    void deleteDesktopGroup_keepsDesktopAndUserGroup() throws Exception {
        Desktop desktop = new Desktop();
        desktop.setDescription(DESKTOP);
        desktop = desktopRepository.save(desktop);
        DesktopGroup desktopGroup = new DesktopGroup();
        desktopGroup.setDescription(DESKTOP_GROUP);
        desktopGroup.getDesktops().add(desktop);
        desktopGroup = desktopGroupRepository.save(desktopGroup);
        UserGroup userGroup = new UserGroup();
        userGroup.setName(GROUP_NAME);
        userGroup.getDesktopGroups().add(desktopGroup);
        userGroup = userGroupRepository.save(userGroup);

        mockMvc.perform(delete("/desktop_group/" + desktopGroup.getId()).with(userJwt(1)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/desktop_group/" + desktopGroup.getId()).with(adminJwt(999)))
                .andExpect(status().isNoContent());

        assertFalse(desktopGroupRepository.existsById(desktopGroup.getId()));
        assertTrue(desktopRepository.existsById(desktop.getId()));
        assertTrue(userGroupRepository.existsById(userGroup.getId()));
        mockMvc.perform(delete("/desktop_group/" + desktopGroup.getId()).with(adminJwt(999)))
                .andExpect(status().isNotFound());
    }
}

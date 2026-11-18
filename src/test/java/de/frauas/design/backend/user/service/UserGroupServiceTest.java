package de.frauas.design.backend.user.service;

import de.frauas.design.backend.user.dto.UserGroupDto;
import de.frauas.design.backend.user.exception.UserGroupNotFoundException;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserGroupService")
class UserGroupServiceTest {

    @Mock
    UserGroupRepository userGroupRepository;

    @Mock
    UserRepository userRepository;

    UserGroupService userGroupService;

    @BeforeEach
    void setUp() {
        userGroupService = new UserGroupService(userGroupRepository, userRepository);
    }

    private User makeUser(Integer id) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setEmail("user" + id + "@e.com");
        u.setPassword("encoded");
        u.setEnabled(true);
        return u;
    }

    @Nested
    @DisplayName("createUserGroup")
    class CreateUserGroup {

        @Test
        @DisplayName("saves and returns DTO")
        void createUserGroup_valid_savesAndReturnsDto() {
            UserGroupDto req = UserGroupDto.builder()
                    .name("Devs")
                    .description("Developers")
                    .build();
            when(userGroupRepository.save(any())).thenAnswer(inv -> {
                UserGroup g = inv.getArgument(0);
                g.setId(1);
                return g;
            });

            UserGroupDto dto = userGroupService.createUserGroup(req);

            assertThat(dto.getName()).isEqualTo("Devs");
        }

        @Test
        @DisplayName("throws when both name and description are blank")
        void createUserGroup_blankNameAndDescription_throws() {
            UserGroupDto req = UserGroupDto.builder().name("  ").description("").build();

            assertThatThrownBy(() -> userGroupService.createUserGroup(req))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(userGroupRepository, never()).save(any());
        }

        @Test
        @DisplayName("falls back to description when name is blank")
        void createUserGroup_blankName_fallsBackToDescription() {
            UserGroupDto req = UserGroupDto.builder()
                    .name(" ")
                    .description("Fallback Desc")
                    .build();
            when(userGroupRepository.save(any())).thenAnswer(inv -> {
                UserGroup g = inv.getArgument(0);
                g.setId(2);
                return g;
            });

            UserGroupDto dto = userGroupService.createUserGroup(req);

            assertThat(dto.getName()).isEqualTo("Fallback Desc");
            assertThat(dto.getDescription()).isEqualTo("Fallback Desc");
        }
    }

    @Nested
    @DisplayName("updateUserGroup")
    class UpdateUserGroup {

        @Test
        @DisplayName("updates fields")
        void updateUserGroup_updatesFields() {
            UserGroup g = new UserGroup();
            g.setId(1);
            g.setName("Old");
            g.setDescription("Old desc");

            when(userGroupRepository.findById(1)).thenReturn(Optional.of(g));
            when(userGroupRepository.save(any())).thenReturn(g);

            UserGroupDto req = UserGroupDto.builder().name("New").build();
            UserGroupDto dto = userGroupService.updateUserGroup(1, req);

            assertThat(dto.getName()).isEqualTo("New");
            assertThat(dto.getDescription()).isEqualTo("Old desc"); // unchanged
        }

        @Test
        @DisplayName("rejects blank name when the resulting group would have no fallback description")
        void updateUserGroup_blankNameAndDescription_throws() {
            UserGroup g = new UserGroup();
            g.setId(1);
            g.setName("Old");
            g.setDescription(null);

            when(userGroupRepository.findById(1)).thenReturn(Optional.of(g));

            UserGroupDto req = UserGroupDto.builder().name(" ").build();

            assertThatThrownBy(() -> userGroupService.updateUserGroup(1, req))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(userGroupRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when group not found")
        void updateUserGroup_notFound_throws() {
            when(userGroupRepository.findById(99)).thenReturn(Optional.empty());

            UserGroupDto req = UserGroupDto.builder().name("New").build();

            assertThatThrownBy(() -> userGroupService.updateUserGroup(99, req))
                    .isInstanceOf(UserGroupNotFoundException.class);
            verify(userGroupRepository, never()).save(any());
        }

        @Test
        @DisplayName("adds and removes members to match the supplied userIds exactly")
        void updateUserGroup_syncsMembership_addsAndRemoves() {
            UserGroup group = new UserGroup();
            group.setId(1);
            group.setName("Devs");

            User staying = makeUser(1);
            User leaving = makeUser(2);
            staying.setGroups(new ArrayList<>(List.of(group)));
            leaving.setGroups(new ArrayList<>(List.of(group)));
            group.setUsers(new ArrayList<>(List.of(staying, leaving)));

            User joining = makeUser(3);
            joining.setGroups(new ArrayList<>());

            when(userGroupRepository.findById(1)).thenReturn(Optional.of(group));
            // desired membership: staying (1) + joining (3), leaving (2) dropped.
            // syncGroupMembership passes a Set (not a List) to findAllById, so stub with a Set too.
            when(userRepository.findAllById(new HashSet<>(List.of(1, 3)))).thenReturn(List.of(staying, joining));
            when(userGroupRepository.save(any())).thenReturn(group);

            UserGroupDto req = UserGroupDto.builder().userIds(List.of(1, 3)).build();
            userGroupService.updateUserGroup(1, req);

            assertThat(leaving.getGroups()).doesNotContain(group);
            assertThat(staying.getGroups()).contains(group);
            assertThat(joining.getGroups()).contains(group);
            verify(userRepository).saveAll(List.of(leaving, joining));
        }

        @Test
        @DisplayName("rejects invalid user IDs instead of silently ignoring them")
        void updateUserGroup_invalidUserIds_throws() {
            UserGroup group = new UserGroup();
            group.setId(1);
            group.setName("Devs");
            group.setDescription("Developers");

            User staying = makeUser(1);
            staying.setGroups(new ArrayList<>(List.of(group)));
            group.setUsers(new ArrayList<>(List.of(staying)));

            when(userGroupRepository.findById(1)).thenReturn(Optional.of(group));
            when(userRepository.findAllById(new LinkedHashSet<>(List.of(1, 99))))
                    .thenReturn(List.of(staying));

            UserGroupDto req = UserGroupDto.builder().userIds(List.of(1, 99)).build();

            assertThatThrownBy(() -> userGroupService.updateUserGroup(1, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown regular user ids: [99]");
            verify(userRepository, never()).saveAll(any());
            verify(userGroupRepository, never()).save(any());
        }

        @Test
        @DisplayName("leaves membership unchanged when userIds is not supplied")
        void updateUserGroup_noUserIds_membershipUntouched() {
            UserGroup group = new UserGroup();
            group.setId(1);
            group.setName("Devs");
            group.setUsers(new ArrayList<>(List.of(makeUser(1))));

            when(userGroupRepository.findById(1)).thenReturn(Optional.of(group));
            when(userGroupRepository.save(any())).thenReturn(group);

            UserGroupDto req = UserGroupDto.builder().description("New desc").build();
            userGroupService.updateUserGroup(1, req);

            verify(userRepository, never()).findAllById(any());
            verify(userRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("getAllUserGroups")
    class GetAllUserGroups {

        @Test
        @DisplayName("returns all groups as DTOs")
        void getAllUserGroups_returnsAllAsDtos() {
            UserGroup g1 = new UserGroup();
            g1.setId(1);
            g1.setName("G1");
            UserGroup g2 = new UserGroup();
            g2.setId(2);
            g2.setName("G2");
            when(userGroupRepository.findAll()).thenReturn(List.of(g1, g2));

            List<UserGroupDto> groups = userGroupService.getAllUserGroups();

            assertThat(groups).hasSize(2);
            assertThat(groups).extracting(UserGroupDto::getName).containsExactlyInAnyOrder("G1", "G2");
        }

        @Test
        @DisplayName("returns empty list when there are no groups")
        void getAllUserGroups_noGroups_returnsEmpty() {
            when(userGroupRepository.findAll()).thenReturn(List.of());

            List<UserGroupDto> groups = userGroupService.getAllUserGroups();

            assertThat(groups).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteUserGroup")
    class DeleteUserGroup {

        @Test
        @DisplayName("removes the group from its members, then deletes it")
        void deleteUserGroup_removesMembershipAndDeletes() {
            UserGroup group = new UserGroup();
            group.setId(3);
            group.setName("g");
            User member = new User();
            member.setId(7);
            member.getGroups().add(group);
            group.getUsers().add(member);
            when(userGroupRepository.findById(3)).thenReturn(Optional.of(group));

            userGroupService.deleteUserGroup(3);

            assertThat(member.getGroups()).isEmpty();
            verify(userRepository).saveAll(List.of(member));
            verify(userGroupRepository).delete(group);
        }

        @Test
        @DisplayName("throws when the group does not exist")
        void deleteUserGroup_notFound_throws() {
            when(userGroupRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userGroupService.deleteUserGroup(99))
                    .isInstanceOf(UserGroupNotFoundException.class);
            verify(userGroupRepository, never()).delete(any());
        }
    }
}

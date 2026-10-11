package de.frauas.design.backend.desktop.service;

import de.frauas.design.backend.desktop.dto.DesktopCreateRequest;
import de.frauas.design.backend.desktop.dto.DesktopDto;
import de.frauas.design.backend.desktop.dto.DesktopGroupCreateRequest;
import de.frauas.design.backend.desktop.dto.DesktopGroupDto;
import de.frauas.design.backend.desktop.exception.DesktopGroupAlreadyExistsException;
import de.frauas.design.backend.desktop.exception.DesktopGroupNotFoundException;
import de.frauas.design.backend.desktop.exception.UserGroupAlreadyAssociatedException;
import de.frauas.design.backend.desktop.model.Desktop;
import de.frauas.design.backend.desktop.model.DesktopGroup;
import de.frauas.design.backend.desktop.repository.DesktopGroupRepository;
import de.frauas.design.backend.desktop.repository.DesktopRepository;
import de.frauas.design.backend.user.exception.UserGroupNotFoundException;
import de.frauas.design.backend.user.model.UserGroup;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DesktopService")
class DesktopServiceTest {

    @Mock
    DesktopRepository desktopRepository;

    @Mock
    DesktopGroupRepository desktopGroupRepository;

    @Mock
    UserGroupRepository userGroupRepository;

    @InjectMocks
    DesktopService desktopService;

    // -------------------------------------------------------------------------
    // Desktop CRUD
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Desktop CRUD")
    class DesktopCrud {

        @Test
        @DisplayName("getDesktopById throws when not found")
        void getDesktopById_notFound_throws() {
            when(desktopRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> desktopService.getDesktopById(99))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("getAllDesktops returns mapped DTOs")
        void getAllDesktops_returnsDtos() {
            Desktop d = new Desktop();
            d.setId(1);
            when(desktopRepository.findAll()).thenReturn(List.of(d));

            List<DesktopDto> result = desktopService.getAllDesktops();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("createDesktop links created sub-groups to the desktop")
        void createDesktop_withGroups_linksGroups() {
            DesktopCreateRequest.SubGroupRequest subGroupRequest = new DesktopCreateRequest.SubGroupRequest();
            subGroupRequest.setDescription("Sub Group");

            DesktopCreateRequest request = new DesktopCreateRequest();
            request.setDescription("Desktop Description");
            request.setGroups(List.of(subGroupRequest));

            when(desktopRepository.save(any())).thenAnswer(invocation -> {
                Desktop desktop = invocation.getArgument(0);
                desktop.setId(10);
                return desktop;
            });
            when(desktopGroupRepository.save(any())).thenAnswer(invocation -> {
                DesktopGroup group = invocation.getArgument(0);
                group.setId(20);
                return group;
            });

            desktopService.createDesktop(request);

            ArgumentCaptor<DesktopGroup> captor = ArgumentCaptor.forClass(DesktopGroup.class);
            verify(desktopGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getDescription()).isEqualTo("Sub Group");
            assertThat(captor.getValue().getDesktops()).extracting(Desktop::getId).containsExactly(10);
        }

        @Test
        @DisplayName("createDesktop rejects a sub-group description that already exists")
        void createDesktop_duplicateSubGroup_throws() {
            DesktopCreateRequest.SubGroupRequest subGroupRequest = new DesktopCreateRequest.SubGroupRequest();
            subGroupRequest.setDescription("Sub Group");
            DesktopCreateRequest request = new DesktopCreateRequest();
            request.setDescription("Desktop Description");
            request.setGroups(List.of(subGroupRequest));
            when(desktopGroupRepository.existsByDescription("Sub Group")).thenReturn(true);

            assertThatThrownBy(() -> desktopService.createDesktop(request))
                    .isInstanceOf(DesktopGroupAlreadyExistsException.class);
            verify(desktopRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // DesktopGroup CRUD
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DesktopGroup CRUD")
    class DesktopGroupCrud {

        @Test
        @DisplayName("createDesktopGroup persists the group")
        void createDesktopGroup_valid_persists() {
            DesktopGroupCreateRequest req = groupRequest("Group 1");
            when(desktopGroupRepository.save(any())).thenAnswer(inv -> {
                DesktopGroup g = inv.getArgument(0);
                g.setId(1);
                return g;
            });

            desktopService.createDesktopGroup(req);

            ArgumentCaptor<DesktopGroup> captor = ArgumentCaptor.forClass(DesktopGroup.class);
            verify(desktopGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getDescription()).isEqualTo("Group 1");
        }

        @Test
        @DisplayName("createDesktopGroup rejects a description that already exists")
        void createDesktopGroup_duplicate_throws() {
            DesktopGroupCreateRequest req = groupRequest("Group 1");
            when(desktopGroupRepository.existsByDescription("Group 1")).thenReturn(true);

            assertThatThrownBy(() -> desktopService.createDesktopGroup(req))
                    .isInstanceOf(DesktopGroupAlreadyExistsException.class);
        }

        private DesktopGroupCreateRequest groupRequest(String description) {
            DesktopGroupCreateRequest req = new DesktopGroupCreateRequest();
            req.setDescription(description);
            return req;
        }

        @Test
        @DisplayName("getAllDesktopGroups returns mapped DTOs")
        void getAllDesktopGroups_returnsDtos() {
            Desktop desktop = new Desktop();
            desktop.setId(7);
            DesktopGroup group = new DesktopGroup();
            group.setId(1);
            group.setDescription("DG1");
            group.getDesktops().add(desktop);
            when(desktopGroupRepository.findAll()).thenReturn(List.of(group));

            List<DesktopGroupDto> result = desktopService.getAllDesktopGroups();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDesktopIds()).containsExactly(7);
        }
    }

    // -------------------------------------------------------------------------
    // User group association
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("addUserGroupToDesktopGroup")
    class AddUserGroupToDesktopGroup {

        @Test
        @DisplayName("throws DesktopGroupNotFoundException when desktop group is missing")
        void desktopGroupMissing_throws() {
            when(desktopGroupRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> desktopService.addUserGroupToDesktopGroup(1, 2))
                    .isInstanceOf(DesktopGroupNotFoundException.class);
        }

        @Test
        @DisplayName("throws UserGroupNotFoundException when user group is missing")
        void userGroupMissing_throws() {
            DesktopGroup dg = new DesktopGroup();
            dg.setId(1);
            when(desktopGroupRepository.findById(1)).thenReturn(Optional.of(dg));
            when(userGroupRepository.findById(2)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> desktopService.addUserGroupToDesktopGroup(1, 2))
                    .isInstanceOf(UserGroupNotFoundException.class);
        }

        @Test
        @DisplayName("throws UserGroupAlreadyAssociatedException when already associated")
        void alreadyAssociated_throws() {
            DesktopGroup dg = new DesktopGroup();
            dg.setId(1);
            UserGroup ug = new UserGroup();
            ug.setId(2);
            ug.getDesktopGroups().add(dg);
            when(desktopGroupRepository.findById(1)).thenReturn(Optional.of(dg));
            when(userGroupRepository.findById(2)).thenReturn(Optional.of(ug));

            assertThatThrownBy(() -> desktopService.addUserGroupToDesktopGroup(1, 2))
                    .isInstanceOf(UserGroupAlreadyAssociatedException.class);
        }

        @Test
        @DisplayName("associates the user group with the desktop group")
        void notAssociated_createsAssociation() {
            DesktopGroup dg = new DesktopGroup();
            dg.setId(1);
            UserGroup ug = new UserGroup();
            ug.setId(2);
            when(desktopGroupRepository.findById(1)).thenReturn(Optional.of(dg));
            when(userGroupRepository.findById(2)).thenReturn(Optional.of(ug));
            when(userGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            desktopService.addUserGroupToDesktopGroup(1, 2);

            assertThat(ug.getDesktopGroups()).contains(dg);
            assertThat(dg.getUserGroups()).contains(ug);
        }
    }
}

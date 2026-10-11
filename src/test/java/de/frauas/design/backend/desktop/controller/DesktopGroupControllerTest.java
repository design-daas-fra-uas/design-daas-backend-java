package de.frauas.design.backend.desktop.controller;

import de.frauas.design.backend.desktop.dto.DesktopGroupCreateRequest;
import de.frauas.design.backend.desktop.dto.DesktopGroupDto;
import de.frauas.design.backend.desktop.exception.DesktopGroupAlreadyExistsException;
import de.frauas.design.backend.desktop.exception.DesktopGroupNotFoundException;
import de.frauas.design.backend.desktop.service.DesktopService;
import de.frauas.design.backend.user.dto.UserGroupDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DesktopGroupController")
class DesktopGroupControllerTest {

    @Mock
    private DesktopService desktopService;

    @InjectMocks
    private DesktopGroupController desktopGroupController;

    @Test
    @DisplayName("getAllDesktopGroups returns 200 with the service response")
    void getAllDesktopGroups_returnsOk() {
        DesktopGroupDto group = DesktopGroupDto.builder().id(1).name("DG1").build();
        when(desktopService.getAllDesktopGroups()).thenReturn(List.of(group));

        var response = desktopGroupController.getAllDesktopGroups();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(group);
    }

    @Test
    @DisplayName("createDesktopGroup returns 201 with the created group")
    void createDesktopGroup_returnsCreated() {
        DesktopGroupCreateRequest request = new DesktopGroupCreateRequest();
        request.setName("DG1");
        request.setDescription("Group 1");
        DesktopGroupDto responseBody = DesktopGroupDto.builder()
                .id(5)
                .name("DG1")
                .description("Group 1")
                .build();
        when(desktopService.createDesktopGroup(request)).thenReturn(responseBody);

        var response = desktopGroupController.createDesktopGroup(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(responseBody);
    }

    @Test
    @DisplayName("createDesktopGroup propagates conflicts from the service")
    void createDesktopGroup_duplicate_propagatesException() {
        DesktopGroupCreateRequest request = new DesktopGroupCreateRequest();
        request.setDescription("Group 1");
        when(desktopService.createDesktopGroup(request)).thenThrow(new DesktopGroupAlreadyExistsException("Group 1"));

        assertThatThrownBy(() -> desktopGroupController.createDesktopGroup(request))
                .isInstanceOf(DesktopGroupAlreadyExistsException.class);
    }

    @Test
    @DisplayName("addUserGroup returns 201 with the updated user groups")
    void addUserGroup_returnsCreated() {
        UserGroupDto userGroup = UserGroupDto.builder().id(2).name("UG1").build();
        when(desktopService.addUserGroupToDesktopGroup(1, 2)).thenReturn(List.of(userGroup));

        var response = desktopGroupController.addUserGroup(1, 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsExactly(userGroup);
    }

    @Test
    @DisplayName("addUserGroup propagates not found errors from the service")
    void addUserGroup_missingDesktopGroup_propagatesException() {
        when(desktopService.addUserGroupToDesktopGroup(1, 2)).thenThrow(new DesktopGroupNotFoundException(1));

        assertThatThrownBy(() -> desktopGroupController.addUserGroup(1, 2))
                .isInstanceOf(DesktopGroupNotFoundException.class);
    }
}

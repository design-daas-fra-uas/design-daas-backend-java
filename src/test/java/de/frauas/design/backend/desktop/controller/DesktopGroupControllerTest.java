package de.frauas.design.backend.desktop.controller;

import de.frauas.design.backend.desktop.dto.DesktopGroupCreateRequest;
import de.frauas.design.backend.desktop.dto.DesktopGroupDto;
import de.frauas.design.backend.desktop.exception.DesktopGroupAlreadyExistsException;
import de.frauas.design.backend.desktop.exception.DesktopGroupNotFoundException;
import de.frauas.design.backend.desktop.service.DesktopService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
        DesktopGroupDto group =
                DesktopGroupDto.builder().id(1).description("DG1").build();
        when(desktopService.getAllDesktopGroups()).thenReturn(List.of(group));

        var response = desktopGroupController.getAllDesktopGroups();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(group);
    }

    @Test
    @DisplayName("createDesktopGroup returns 201 with no body")
    void createDesktopGroup_returnsCreated() {
        DesktopGroupCreateRequest request = new DesktopGroupCreateRequest();
        request.setDescription("Group 1");

        var response = desktopGroupController.createDesktopGroup(request);

        verify(desktopService).createDesktopGroup(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("createDesktopGroup propagates conflicts from the service")
    void createDesktopGroup_duplicate_propagatesException() {
        DesktopGroupCreateRequest request = new DesktopGroupCreateRequest();
        request.setDescription("Group 1");
        doThrow(new DesktopGroupAlreadyExistsException("Group 1"))
                .when(desktopService)
                .createDesktopGroup(request);

        assertThatThrownBy(() -> desktopGroupController.createDesktopGroup(request))
                .isInstanceOf(DesktopGroupAlreadyExistsException.class);
    }

    @Test
    @DisplayName("addUserGroup returns 201 with no body")
    void addUserGroup_returnsCreated() {
        var response = desktopGroupController.addUserGroup(1, 2);

        verify(desktopService).addUserGroupToDesktopGroup(1, 2);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("addUserGroup propagates not found errors from the service")
    void addUserGroup_missingDesktopGroup_propagatesException() {
        doThrow(new DesktopGroupNotFoundException(1)).when(desktopService).addUserGroupToDesktopGroup(1, 2);

        assertThatThrownBy(() -> desktopGroupController.addUserGroup(1, 2))
                .isInstanceOf(DesktopGroupNotFoundException.class);
    }
}

package de.frauas.design.backend.permission.service;

import de.frauas.design.backend.permission.dto.EndpointCreateRequest;
import de.frauas.design.backend.permission.dto.EndpointGroupCreateRequest;
import de.frauas.design.backend.permission.dto.EndpointGroupDto;
import de.frauas.design.backend.permission.dto.EndpointGroupPatchRequest;
import de.frauas.design.backend.permission.dto.PermissionRuleCreateRequest;
import de.frauas.design.backend.permission.dto.PermissionRuleDto;
import de.frauas.design.backend.permission.dto.PermissionRuleKind;
import de.frauas.design.backend.permission.exception.EndpointAlreadyExistsException;
import de.frauas.design.backend.permission.exception.EndpointGroupAlreadyExistsException;
import de.frauas.design.backend.permission.exception.EndpointNotFoundException;
import de.frauas.design.backend.permission.exception.PermissionRuleAlreadyExistsException;
import de.frauas.design.backend.permission.exception.PermissionRuleNotFoundException;
import de.frauas.design.backend.permission.model.Endpoint;
import de.frauas.design.backend.permission.model.EndpointGroup;
import de.frauas.design.backend.permission.model.EndpointGroupUserGroupAccess;
import de.frauas.design.backend.permission.model.EndpointUserAccess;
import de.frauas.design.backend.permission.repository.EndpointGroupRepository;
import de.frauas.design.backend.permission.repository.EndpointGroupUserAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointGroupUserGroupAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointRepository;
import de.frauas.design.backend.permission.repository.EndpointUserAccessRepository;
import de.frauas.design.backend.permission.repository.EndpointUserGroupAccessRepository;
import de.frauas.design.backend.user.exception.AccountNotFoundException;
import de.frauas.design.backend.user.exception.UserGroupNotFoundException;
import de.frauas.design.backend.user.repository.UserGroupRepository;
import de.frauas.design.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionManagementService")
class PermissionManagementServiceTest {

    @Mock
    EndpointRepository endpointRepository;

    @Mock
    EndpointGroupRepository endpointGroupRepository;

    @Mock
    EndpointUserAccessRepository endpointUserAccessRepo;

    @Mock
    EndpointUserGroupAccessRepository endpointUserGroupAccessRepo;

    @Mock
    EndpointGroupUserAccessRepository endpointGroupUserAccessRepo;

    @Mock
    EndpointGroupUserGroupAccessRepository endpointGroupUserGroupAccessRepo;

    @Mock
    UserRepository userRepository;

    @Mock
    UserGroupRepository userGroupRepository;

    PermissionManagementService service;

    @BeforeEach
    void setUp() {
        service = new PermissionManagementService(
                endpointRepository,
                endpointGroupRepository,
                endpointUserAccessRepo,
                endpointUserGroupAccessRepo,
                endpointGroupUserAccessRepo,
                endpointGroupUserGroupAccessRepo,
                userRepository,
                userGroupRepository);
    }

    private Endpoint makeEndpoint(Integer id, String functionName) {
        Endpoint e = new Endpoint();
        e.setId(id);
        e.setFunctionName(functionName);
        return e;
    }

    private EndpointGroup makeEndpointGroup(Integer id, String name, Endpoint... endpoints) {
        EndpointGroup g = new EndpointGroup();
        g.setId(id);
        g.setName(name);
        g.setEndpoints(new ArrayList<>(List.of(endpoints)));
        return g;
    }

    private PermissionRuleCreateRequest makeRuleRequest(
            PermissionRuleKind kind, Integer subjectId, Integer targetId, boolean allow) {
        PermissionRuleCreateRequest req = new PermissionRuleCreateRequest();
        req.setKind(kind);
        req.setSubjectId(subjectId);
        req.setTargetId(targetId);
        req.setAllow(allow);
        return req;
    }

    @Nested
    @DisplayName("createEndpoint")
    class CreateEndpoint {

        @Test
        @DisplayName("saves a new endpoint")
        void createEndpoint_valid_saves() {
            EndpointCreateRequest req = new EndpointCreateRequest();
            req.setFunctionName("vm_start");
            req.setDescription("Start a VM");
            when(endpointRepository.existsByFunctionName("vm_start")).thenReturn(false);

            service.createEndpoint(req);

            ArgumentCaptor<Endpoint> captor = ArgumentCaptor.forClass(Endpoint.class);
            verify(endpointRepository).save(captor.capture());
            assertThat(captor.getValue().getFunctionName()).isEqualTo("vm_start");
            assertThat(captor.getValue().getDescription()).isEqualTo("Start a VM");
        }

        @Test
        @DisplayName("throws on a duplicate function name")
        void createEndpoint_duplicate_throws() {
            EndpointCreateRequest req = new EndpointCreateRequest();
            req.setFunctionName("vm_start");
            when(endpointRepository.existsByFunctionName("vm_start")).thenReturn(true);

            assertThatThrownBy(() -> service.createEndpoint(req)).isInstanceOf(EndpointAlreadyExistsException.class);
            verify(endpointRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteEndpoint")
    class DeleteEndpoint {

        @Test
        @DisplayName("deletes its rules, removes it from endpoint groups, then deletes it")
        void deleteEndpoint_existing_cleansUpAndDeletes() {
            Endpoint endpoint = makeEndpoint(1, "vm_start");
            EndpointGroup group = makeEndpointGroup(5, "vm-control", endpoint);
            when(endpointRepository.findById(1)).thenReturn(Optional.of(endpoint));
            when(endpointGroupRepository.findAllByEndpoints_Id(1)).thenReturn(List.of(group));

            service.deleteEndpoint(1);

            verify(endpointUserAccessRepo).deleteAllByEndpoint_Id(1);
            verify(endpointUserGroupAccessRepo).deleteAllByEndpoint_Id(1);
            assertThat(group.getEndpoints()).isEmpty();
            verify(endpointRepository).delete(endpoint);
        }

        @Test
        @DisplayName("throws when the endpoint does not exist")
        void deleteEndpoint_notFound_throws() {
            when(endpointRepository.findById(9)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteEndpoint(9)).isInstanceOf(EndpointNotFoundException.class);
            verify(endpointRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("endpoint groups")
    class EndpointGroups {

        @Test
        @DisplayName("createEndpointGroup saves the group with its endpoints")
        void createEndpointGroup_valid_saves() {
            Endpoint endpoint = makeEndpoint(1, "vm_start");
            EndpointGroupCreateRequest req = new EndpointGroupCreateRequest();
            req.setName("vm-control");
            req.setEndpointIds(List.of(1));
            when(endpointGroupRepository.existsByName("vm-control")).thenReturn(false);
            when(endpointRepository.findAllById(any())).thenReturn(List.of(endpoint));

            service.createEndpointGroup(req);

            ArgumentCaptor<EndpointGroup> captor = ArgumentCaptor.forClass(EndpointGroup.class);
            verify(endpointGroupRepository).save(captor.capture());
            assertThat(captor.getValue().getEndpoints()).containsExactly(endpoint);
        }

        @Test
        @DisplayName("createEndpointGroup throws on unknown endpoint ids")
        void createEndpointGroup_unknownEndpoint_throws() {
            EndpointGroupCreateRequest req = new EndpointGroupCreateRequest();
            req.setName("vm-control");
            req.setEndpointIds(List.of(1, 2));
            when(endpointGroupRepository.existsByName("vm-control")).thenReturn(false);
            when(endpointRepository.findAllById(any())).thenReturn(List.of(makeEndpoint(1, "vm_start")));

            assertThatThrownBy(() -> service.createEndpointGroup(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2");
            verify(endpointGroupRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateEndpointGroup throws when the new name is taken")
        void updateEndpointGroup_duplicateName_throws() {
            when(endpointGroupRepository.findById(5)).thenReturn(Optional.of(makeEndpointGroup(5, "old")));
            when(endpointGroupRepository.existsByName("taken")).thenReturn(true);
            EndpointGroupPatchRequest req = new EndpointGroupPatchRequest();
            req.setName("taken");

            assertThatThrownBy(() -> service.updateEndpointGroup(5, req))
                    .isInstanceOf(EndpointGroupAlreadyExistsException.class);
        }

        @Test
        @DisplayName("updateEndpointGroup replaces the endpoints and keeps omitted fields")
        void updateEndpointGroup_replacesEndpoints() {
            Endpoint stop = makeEndpoint(2, "vm_stop");
            EndpointGroup group = makeEndpointGroup(5, "vm-control", makeEndpoint(1, "vm_start"));
            when(endpointGroupRepository.findById(5)).thenReturn(Optional.of(group));
            when(endpointRepository.findAllById(any())).thenReturn(List.of(stop));
            EndpointGroupPatchRequest req = new EndpointGroupPatchRequest();
            req.setEndpointIds(List.of(2));

            EndpointGroupDto dto = service.updateEndpointGroup(5, req);

            assertThat(dto.getName()).isEqualTo("vm-control");
            assertThat(dto.getEndpointIds()).containsExactly(2);
        }

        @Test
        @DisplayName("deleteEndpointGroup deletes its rules, then the group")
        void deleteEndpointGroup_existing_deletes() {
            EndpointGroup group = makeEndpointGroup(5, "vm-control");
            when(endpointGroupRepository.findById(5)).thenReturn(Optional.of(group));

            service.deleteEndpointGroup(5);

            verify(endpointGroupUserAccessRepo).deleteAllByEndpointGroup_Id(5);
            verify(endpointGroupUserGroupAccessRepo).deleteAllByEndpointGroup_Id(5);
            verify(endpointGroupRepository).delete(group);
        }
    }

    @Nested
    @DisplayName("createRule")
    class CreateRule {

        @Test
        @DisplayName("creates a USER_ENDPOINT rule")
        void createRule_userEndpoint_saves() {
            Endpoint endpoint = makeEndpoint(1, "vm_start");
            when(userRepository.existsById(7)).thenReturn(true);
            when(endpointRepository.findById(1)).thenReturn(Optional.of(endpoint));
            when(endpointUserAccessRepo.existsByUserIdAndEndpoint_Id(7, 1)).thenReturn(false);
            when(endpointUserAccessRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createRule(makeRuleRequest(PermissionRuleKind.USER_ENDPOINT, 7, 1, true));

            ArgumentCaptor<EndpointUserAccess> captor = ArgumentCaptor.forClass(EndpointUserAccess.class);
            verify(endpointUserAccessRepo).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(7);
            assertThat(captor.getValue().getEndpoint()).isSameAs(endpoint);
            assertThat(captor.getValue().isAllowAccess()).isTrue();
        }

        @Test
        @DisplayName("creates a USER_GROUP_ENDPOINT_GROUP rule")
        void createRule_groupEndpointGroup_saves() {
            EndpointGroup group = makeEndpointGroup(5, "vm-control");
            when(userGroupRepository.existsById(3)).thenReturn(true);
            when(endpointGroupRepository.findById(5)).thenReturn(Optional.of(group));
            when(endpointGroupUserGroupAccessRepo.existsByUserGroupIdAndEndpointGroup_Id(3, 5))
                    .thenReturn(false);
            when(endpointGroupUserGroupAccessRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createRule(makeRuleRequest(PermissionRuleKind.USER_GROUP_ENDPOINT_GROUP, 3, 5, false));

            ArgumentCaptor<EndpointGroupUserGroupAccess> captor =
                    ArgumentCaptor.forClass(EndpointGroupUserGroupAccess.class);
            verify(endpointGroupUserGroupAccessRepo).save(captor.capture());
            assertThat(captor.getValue().getUserGroupId()).isEqualTo(3);
            assertThat(captor.getValue().isAllowAccess()).isFalse();
        }

        @Test
        @DisplayName("throws when the user does not exist")
        void createRule_unknownUser_throws() {
            when(userRepository.existsById(7)).thenReturn(false);

            assertThatThrownBy(() -> service.createRule(makeRuleRequest(PermissionRuleKind.USER_ENDPOINT, 7, 1, true)))
                    .isInstanceOf(AccountNotFoundException.class);
            verify(endpointUserAccessRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws when the user group does not exist")
        void createRule_unknownUserGroup_throws() {
            when(userGroupRepository.existsById(3)).thenReturn(false);

            assertThatThrownBy(() ->
                            service.createRule(makeRuleRequest(PermissionRuleKind.USER_GROUP_ENDPOINT, 3, 1, true)))
                    .isInstanceOf(UserGroupNotFoundException.class);
        }

        @Test
        @DisplayName("throws on a duplicate rule")
        void createRule_duplicate_throws() {
            when(userRepository.existsById(7)).thenReturn(true);
            when(endpointRepository.findById(1)).thenReturn(Optional.of(makeEndpoint(1, "vm_start")));
            when(endpointUserAccessRepo.existsByUserIdAndEndpoint_Id(7, 1)).thenReturn(true);

            assertThatThrownBy(() -> service.createRule(makeRuleRequest(PermissionRuleKind.USER_ENDPOINT, 7, 1, true)))
                    .isInstanceOf(PermissionRuleAlreadyExistsException.class);
            verify(endpointUserAccessRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getAllRules / deleteRule")
    class RulesListAndDelete {

        @Test
        @DisplayName("getAllRules maps every kind with its target name")
        void getAllRules_mapsAllKinds() {
            EndpointUserAccess rule = new EndpointUserAccess();
            rule.setId(1);
            rule.setUserId(7);
            rule.setEndpoint(makeEndpoint(1, "vm_start"));
            rule.setAllowAccess(true);
            when(endpointUserAccessRepo.findAll()).thenReturn(List.of(rule));

            List<PermissionRuleDto> rules = service.getAllRules();

            assertThat(rules).singleElement().satisfies(dto -> {
                assertThat(dto.getKind()).isEqualTo(PermissionRuleKind.USER_ENDPOINT);
                assertThat(dto.getSubjectId()).isEqualTo(7);
                assertThat(dto.getTargetName()).isEqualTo("vm_start");
                assertThat(dto.isAllow()).isTrue();
            });
        }

        @Test
        @DisplayName("deleteRule deletes from the table of the given kind")
        void deleteRule_existing_deletes() {
            when(endpointGroupUserAccessRepo.existsById(4)).thenReturn(true);

            service.deleteRule(PermissionRuleKind.USER_ENDPOINT_GROUP, 4);

            verify(endpointGroupUserAccessRepo).deleteById(4);
            verify(endpointUserAccessRepo, never()).deleteById(any());
        }

        @Test
        @DisplayName("deleteRule throws when the rule does not exist")
        void deleteRule_notFound_throws() {
            when(endpointUserAccessRepo.existsById(4)).thenReturn(false);

            assertThatThrownBy(() -> service.deleteRule(PermissionRuleKind.USER_ENDPOINT, 4))
                    .isInstanceOf(PermissionRuleNotFoundException.class);
            verify(endpointUserAccessRepo, never()).deleteById(any());
        }
    }
}

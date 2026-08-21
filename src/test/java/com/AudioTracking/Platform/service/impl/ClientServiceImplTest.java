package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.client.ClientResponse;
import com.AudioTracking.Platform.dto.client.CreateClientRequest;
import com.AudioTracking.Platform.entity.Client;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ClientMapper;
import com.AudioTracking.Platform.repository.ClientRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ClientMapper clientMapper;

    private ClientServiceImpl clientService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clientService = new ClientServiceImpl(clientRepository, userRepository, projectRepository, clientMapper);
    }

    @Test
    void createClient_assignsAuthenticatedUserAsOwner() {
        CreateClientRequest request = new CreateClientRequest("John Smith", null, null, null);
        Client mapped = new Client();
        when(clientMapper.toEntity(request)).thenReturn(mapped);

        User ownerRef = new User();
        ownerRef.setId(ownerId);
        when(userRepository.getReferenceById(ownerId)).thenReturn(ownerRef);

        Client saved = new Client();
        saved.setId(clientId);
        when(clientRepository.save(mapped)).thenReturn(saved);
        ClientResponse expected = mock(ClientResponse.class);
        when(clientMapper.toResponse(saved)).thenReturn(expected);

        ClientResponse result = clientService.createClient(ownerId, request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(ownerRef);
    }

    @Test
    void getClient_notOwnedByCaller_throwsNotFound_sameAsNonexistentId() {
        when(clientRepository.findByIdAndUserId(clientId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClient(otherUserId, clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteClient_notOwnedByCaller_throwsNotFound_andNeverDeletes() {
        when(clientRepository.findByIdAndUserId(clientId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.deleteClient(otherUserId, clientId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(clientRepository, never()).delete(any());
    }

    @Test
    void deleteClient_unassignsEveryProjectThatReferencedIt_withoutDeletingThem() {
        Client existing = new Client();
        existing.setId(clientId);
        when(clientRepository.findByIdAndUserId(clientId, ownerId)).thenReturn(Optional.of(existing));

        Project projectA = new Project();
        projectA.setClient(existing);
        Project projectB = new Project();
        projectB.setClient(existing);
        when(projectRepository.findAllByClientId(clientId)).thenReturn(List.of(projectA, projectB));

        clientService.deleteClient(ownerId, clientId);

        assertThat(projectA.getClient()).isNull();
        assertThat(projectB.getClient()).isNull();
        verify(projectRepository, never()).delete(any());
        verify(clientRepository).delete(existing);
    }

    @Test
    void deleteClient_ownedByCaller_withNoProjects_justDeletesIt() {
        Client existing = new Client();
        existing.setId(clientId);
        when(clientRepository.findByIdAndUserId(clientId, ownerId)).thenReturn(Optional.of(existing));
        when(projectRepository.findAllByClientId(clientId)).thenReturn(List.of());

        clientService.deleteClient(ownerId, clientId);

        verify(clientRepository).delete(existing);
    }
}

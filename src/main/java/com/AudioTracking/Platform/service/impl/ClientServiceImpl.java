package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.client.ClientResponse;
import com.AudioTracking.Platform.dto.client.CreateClientRequest;
import com.AudioTracking.Platform.dto.client.UpdateClientRequest;
import com.AudioTracking.Platform.entity.AnalyticsEventType;
import com.AudioTracking.Platform.entity.Client;
import com.AudioTracking.Platform.entity.Project;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.ResourceNotFoundException;
import com.AudioTracking.Platform.mapper.ClientMapper;
import com.AudioTracking.Platform.repository.ClientRepository;
import com.AudioTracking.Platform.repository.ProjectRepository;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.service.AnalyticsService;
import com.AudioTracking.Platform.service.ClientService;
import com.AudioTracking.Platform.util.UsernameGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AnalyticsService analyticsService;
    private final ClientMapper clientMapper;
    private final UsernameGenerator usernameGenerator;

    public ClientServiceImpl(ClientRepository clientRepository, UserRepository userRepository,
                              ProjectRepository projectRepository, AnalyticsService analyticsService,
                              ClientMapper clientMapper, UsernameGenerator usernameGenerator) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.analyticsService = analyticsService;
        this.clientMapper = clientMapper;
        this.usernameGenerator = usernameGenerator;
    }

    @Override
    public ClientResponse createClient(UUID ownerId, CreateClientRequest request) {
        Client client = clientMapper.toEntity(request);
        // getReferenceById avoids an extra SELECT: the caller is already an authenticated user
        // resolved from the JWT, so we only need their id to set the FK, not the full row.
        client.setUser(userRepository.getReferenceById(ownerId));
        client.setLinkedUser(resolveLinkedUserOrNull(request.email()));
        Client saved = clientRepository.save(client);
        analyticsService.record(ownerId, AnalyticsEventType.CLIENT_CREATED, null, null);
        return clientMapper.toResponse(saved);
    }

    @Override
    public List<ClientResponse> getClients(UUID ownerId) {
        return clientMapper.toResponseList(clientRepository.findAllByUserIdOrderByCreatedAtDesc(ownerId));
    }

    @Override
    public ClientResponse getClient(UUID ownerId, UUID clientId) {
        return clientMapper.toResponse(findOwnedOrThrow(ownerId, clientId));
    }

    @Override
    public ClientResponse updateClient(UUID ownerId, UUID clientId, UpdateClientRequest request) {
        Client existing = findOwnedOrThrow(ownerId, clientId);
        clientMapper.updateEntity(request, existing);
        // Re-resolved on every update, not just once at creation -- an edited email re-links to
        // whatever account matches it now (a different existing User, a freshly provisioned one,
        // or null if the email was cleared), same as changing any other field.
        existing.setLinkedUser(resolveLinkedUserOrNull(request.email()));
        return clientMapper.toResponse(clientRepository.save(existing));
    }

    @Override
    @Transactional // unassigning every affected project + the delete itself must succeed together
    public void deleteClient(UUID ownerId, UUID clientId) {
        Client existing = findOwnedOrThrow(ownerId, clientId);
        // Projects must survive their client being deleted — just lose the association, not get
        // deleted. Same pattern as ProjectServiceImpl#deleteProject unassigning Assets.
        for (Project project : projectRepository.findAllByClientId(clientId)) {
            project.setClient(null);
        }
        clientRepository.delete(existing);
    }

    private Client findOwnedOrThrow(UUID ownerId, UUID clientId) {
        return clientRepository.findByIdAndUserId(clientId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Client with id '" + clientId + "' not found"));
    }

    // Resolves (and auto-provisions if needed) the User a Client can log in as -- see
    // Client.linkedUser for the full contract this implements.
    private User resolveLinkedUserOrNull(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(email)
                .orElseGet(() -> provisionClientOnlyUser(email));
    }

    private User provisionClientOnlyUser(String email) {
        User user = new User();
        user.setUsername(usernameGenerator.generateUniqueUsername(email));
        user.setEmail(email);
        // No passwordHash, no googleId -- the same "Google-login-only, until they authenticate
        // that way" shape AuthServiceImpl#linkOrCreateGoogleUser produces for a fresh Google
        // signup. That's what lets this exact account log in the moment they complete Google
        // sign-in with this same email, with zero changes needed to the login flow itself.
        return userRepository.save(user);
    }
}

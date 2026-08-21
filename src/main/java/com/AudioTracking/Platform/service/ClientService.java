package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.client.ClientResponse;
import com.AudioTracking.Platform.dto.client.CreateClientRequest;
import com.AudioTracking.Platform.dto.client.UpdateClientRequest;

import java.util.List;
import java.util.UUID;

public interface ClientService {

    ClientResponse createClient(UUID ownerId, CreateClientRequest request);

    List<ClientResponse> getClients(UUID ownerId);

    ClientResponse getClient(UUID ownerId, UUID clientId);

    ClientResponse updateClient(UUID ownerId, UUID clientId, UpdateClientRequest request);

    void deleteClient(UUID ownerId, UUID clientId);
}

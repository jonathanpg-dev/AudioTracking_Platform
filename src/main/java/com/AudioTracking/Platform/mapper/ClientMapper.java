package com.AudioTracking.Platform.mapper;

import com.AudioTracking.Platform.dto.client.ClientResponse;
import com.AudioTracking.Platform.dto.client.CreateClientRequest;
import com.AudioTracking.Platform.dto.client.UpdateClientRequest;
import com.AudioTracking.Platform.entity.Client;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClientMapper {

    public Client toEntity(CreateClientRequest request) {
        Client client = new Client();
        client.setName(request.name());
        client.setEmail(request.email());
        client.setCompany(request.company());
        client.setNotes(request.notes());
        return client;
    }

    public void updateEntity(UpdateClientRequest request, Client existing) {
        existing.setName(request.name());
        existing.setEmail(request.email());
        existing.setCompany(request.company());
        existing.setNotes(request.notes());
    }

    public ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getCompany(),
                client.getNotes(),
                client.getCreatedAt(),
                client.getUpdatedAt());
    }

    public List<ClientResponse> toResponseList(List<Client> clients) {
        return clients.stream().map(this::toResponse).toList();
    }
}

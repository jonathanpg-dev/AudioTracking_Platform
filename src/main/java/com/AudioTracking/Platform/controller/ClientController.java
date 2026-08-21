package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.dto.client.ClientResponse;
import com.AudioTracking.Platform.dto.client.CreateClientRequest;
import com.AudioTracking.Platform.dto.client.UpdateClientRequest;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                         @Valid @RequestBody CreateClientRequest request) {
        ClientResponse response = clientService.createClient(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getClients(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(clientService.getClients(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClient(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                      @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getClient(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                         @PathVariable UUID id,
                                                         @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.updateClient(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@AuthenticationPrincipal CustomUserDetails currentUser,
                                              @PathVariable UUID id) {
        clientService.deleteClient(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}

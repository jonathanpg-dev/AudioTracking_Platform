package com.AudioTracking.Platform.Controllers;

import com.AudioTracking.Platform.Entities.User;
import com.AudioTracking.Platform.Entities.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class UserController {
    @Autowired
    private UserRepo userRepo;

    @PostMapping("api/v1/user")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User response = userRepo.save(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("api/v1/users")
    public List<User> createUsers(@RequestBody List<User> users) {
        return userRepo.saveAll(users);
    }

    @GetMapping("api/v1/users")
    public List<User> getAllUsers() {
        List<User> usersList = userRepo.findAll();
        return usersList;
    }

    @GetMapping("api/v1/user/{id}")
    public User getAllUsers(@PathVariable UUID id) {
        User user = userRepo.findById(id).orElse(null);
        return user;
    }

}

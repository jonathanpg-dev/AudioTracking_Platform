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
    public User getUserById(@PathVariable UUID id) {
        User user = userRepo.findById(id).orElse(null);
        return user;
    }

    @PutMapping("api/v1/user/{id}")
    public User completeUserUpdate(@PathVariable UUID id, @RequestBody User updatedUser) {
        User existingUser = userRepo.findById(id).orElse(null);

        if(existingUser != null) {
            existingUser.setName(updatedUser.getName());
            existingUser.setEmail(updatedUser.getEmail());
            userRepo.save(existingUser);
            return existingUser;
        } else {
            return null;
        }
    }

}

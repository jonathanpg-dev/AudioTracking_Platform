package com.AudioTracking.Platform.Controllers;

import com.AudioTracking.Platform.Entities.User;
import com.AudioTracking.Platform.Entities.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class UserController {
    @Autowired
    private UserRepo userRepo;

    @PostMapping("api/v1/users")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User response = userRepo.save(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("api/v1/users/batch")
    public List<User> createUsers(@RequestBody List<User> users) {
        return userRepo.saveAll(users);
    }

    @GetMapping("api/v1/users")
    public List<User> getAllUsers() {
        List<User> usersList = userRepo.findAll();
        return usersList;
    }

    @GetMapping("api/v1/users/{id}")
    public User getUserById(@PathVariable UUID id) {
        User user = userRepo.findById(id).orElse(null);
        return user;
    }

    @GetMapping("api/v1/users/sort")
    public List<User> getSortedUsers(@RequestParam String sortDir, @RequestParam String sortBy)
    {
        Sort.Direction direction = sortDir.equals("asc")?Sort.Direction.ASC:Sort.Direction.DESC;
        return userRepo.findAll(Sort.by(direction, sortBy));
    }

    @PutMapping("api/v1/users/{id}")
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

    @DeleteMapping("api/v1/users/{id}")
    public void deleteUser(@PathVariable UUID id) {
        userRepo.deleteById(id);
    }



}

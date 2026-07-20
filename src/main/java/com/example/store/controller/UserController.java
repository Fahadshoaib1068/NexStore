package com.example.store.controller;

import com.example.store.model.User;
import com.example.store.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/promote")
    public ResponseEntity<?> promoteUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String newRole = body.get("role");

        if (username == null || newRole == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and role are required"));
        }

        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Integer roleId = userRepository.getRoleIdByName(newRole);
        if (roleId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + newRole));
        }

        userRepository.updateUserRole(user.getUser_id(), roleId);

        return ResponseEntity.ok(Map.of("message", "User role updated successfully"));

    }

}

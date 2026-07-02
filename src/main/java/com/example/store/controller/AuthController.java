package com.example.store.controller;

import com.example.store.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import util.JwtUtil;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository        userRepository;
    private final JwtUtil               jwtUtil;
    private final PasswordEncoder       passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository        = userRepository;
        this.jwtUtil               = jwtUtil;
        this.passwordEncoder       = passwordEncoder;
    }

    // ─── LOGIN ────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

//        System.out.println("DEBUG - Attempting login for: " + username);
//        System.out.println("DEBUG - Password entered: " + password);
//        System.out.println("DEBUG - BCrypt test: " + passwordEncoder.matches(
//                password,
//                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh23"
//        ));

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // Get role from authenticated user
            String role = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("ROLE_CUSTOMER")
                    .replace("ROLE_", "");

            String token = jwtUtil.generateToken(username, role);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "username", username,
                    "role", role
            ));

        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid username or password"
            ));
        }
    }

    // ─── REGISTER ─────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");

        // Validate fields
        if (username == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Username, email and password are required"
            ));
        }

        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Username already taken"
            ));
        }

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Email already registered"
            ));
        }

        // Hash password and save user
        String hashedPassword = passwordEncoder.encode(password);
        Integer userId = userRepository.save(username, email, hashedPassword);

        if (userId == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to create user"
            ));
        }

        // Assign CUSTOMER role by default (role_id = 4)
        Integer roleId = userRepository.getRoleIdByName("CUSTOMER");
        userRepository.assignRole(userId, roleId);

        return ResponseEntity.ok(Map.of(
                "message", "Registration successful! You can now login.",
                "username", username,
                "role", "CUSTOMER"
        ));
    }

    @GetMapping("/generate-hash")
    public ResponseEntity<?> generateHash(@RequestParam String password) {
        return ResponseEntity.ok(Map.of("hash", passwordEncoder.encode(password)));
    }
}
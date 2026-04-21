package com.streamvault.command_service.controller;

import com.streamvault.command_service.domain.entity.User;
import com.streamvault.command_service.repository.UserRepository;
import com.streamvault.command_service.security.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email is already registered.");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials.");
        }

        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    public record RegisterRequest(
            @NotBlank String fullName,
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(String token) {}
}

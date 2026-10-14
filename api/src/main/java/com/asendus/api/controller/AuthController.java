package com.asendus.api.controller;

import com.asendus.api.dto.AuthRequestDto;
import com.asendus.api.dto.AuthResponseDto;
import com.asendus.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/google")
    public ResponseEntity<AuthResponseDto> googleAuth(@Valid @RequestBody AuthRequestDto request) {
        return ResponseEntity.ok(authService.googleLogin(request));
    }
}

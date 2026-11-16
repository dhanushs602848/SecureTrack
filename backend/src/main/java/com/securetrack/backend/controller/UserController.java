package com.securetrack.backend.controller;

import com.securetrack.backend.dto.ApiResponse;
import com.securetrack.backend.dto.UserDto;
import com.securetrack.backend.security.UserPrincipal;
import com.securetrack.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserDto user = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Current user profile retrieved", user));
    }
}

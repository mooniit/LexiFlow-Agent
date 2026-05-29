package com.example.lexiflow.user.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.security.JwtService;
import com.example.lexiflow.user.service.UserAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserAuthService authService;
    private final JwtService jwtService;

    public AuthController(UserAuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        CurrentUser user = authService.authenticate(request.username(), request.password());
        return ApiResponse.ok(LoginResponse.from(jwtService.createToken(user), user));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(UserProfile.from(user));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, UserProfile user) {
        static LoginResponse from(String token, CurrentUser user) {
            return new LoginResponse(token, UserProfile.from(user));
        }
    }

    public record UserProfile(Long id, String username, String displayName, Long departmentId,
                              List<String> roles, List<String> permissions) {
        static UserProfile from(CurrentUser user) {
            return new UserProfile(user.id(), user.username(), user.displayName(), user.departmentId(), user.roles(), user.permissions());
        }
    }
}

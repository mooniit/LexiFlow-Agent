package com.example.lexiflow.user.api;

import com.example.lexiflow.common.model.ApiResponse;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.user.service.UserAdminService;
import com.example.lexiflow.user.service.UserAdminService.CreateUserCommand;
import com.example.lexiflow.user.service.UserAdminService.PermissionSummary;
import com.example.lexiflow.user.service.UserAdminService.RoleSummary;
import com.example.lexiflow.user.service.UserAdminService.UpdateUserCommand;
import com.example.lexiflow.user.service.UserAdminService.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserSummary>> users(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userAdminService.listUsers(user));
    }

    @PostMapping("/users")
    public ApiResponse<UserSummary> createUser(@Valid @RequestBody CreateUserRequest request,
                                               @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userAdminService.createUser(new CreateUserCommand(
                request.username(),
                request.password(),
                request.displayName(),
                request.departmentId(),
                request.enabled(),
                request.roles()
        ), user));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserSummary> updateUser(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUserRequest request,
                                               @AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userAdminService.updateUser(id, new UpdateUserCommand(
                request.password(),
                request.displayName(),
                request.departmentId(),
                request.enabled(),
                request.roles()
        ), user));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        userAdminService.deleteUser(id, user);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleSummary>> roles(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userAdminService.listRoles(user));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionSummary>> permissions(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(userAdminService.listPermissions(user));
    }

    public record CreateUserRequest(@NotBlank String username, @NotBlank String password,
                                    @NotBlank String displayName, Long departmentId, Boolean enabled,
                                    List<String> roles) {
    }

    public record UpdateUserRequest(String password, @NotBlank String displayName, Long departmentId,
                                    Boolean enabled, List<String> roles) {
    }
}

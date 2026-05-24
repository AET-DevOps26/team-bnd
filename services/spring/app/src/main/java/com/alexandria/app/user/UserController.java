package com.alexandria.app.user;

import com.alexandria.app.exception.UserNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.security.Principal;

@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users", description = "User account management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user account", description = "Users can only delete their own account")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @ApiResponse(responseCode = "403", description = "Cannot delete other users")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id, Principal principal) {
        String oidcSubject = principal.getName();
        User currentUser = userService.findByOidcSubject(oidcSubject)
                .orElseThrow(() -> new UserNotFoundException(oidcSubject));

        if (!currentUser.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
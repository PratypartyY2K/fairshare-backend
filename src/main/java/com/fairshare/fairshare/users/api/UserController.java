package com.fairshare.fairshare.users.api;

import com.fairshare.fairshare.common.BadRequestException;
import com.fairshare.fairshare.common.NotFoundException;
import com.fairshare.fairshare.users.model.User;
import com.fairshare.fairshare.users.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "Users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create user",
            description = "Create a user identity and use its id as X-User-Id in authenticated/group-scoped requests."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    public UserResponse create(@RequestBody @Valid CreateUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new BadRequestException("User with email already exists");
        }

        User user = userRepository.save(new User(request.name().trim(), normalizedEmail));
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public UserResponse get(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new BadRequestException("Email must not be blank");
        }
        return normalized;
    }
}

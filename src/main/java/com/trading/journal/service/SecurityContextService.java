package com.trading.journal.service;

import com.trading.journal.entity.User;
import com.trading.journal.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Service for accessing the current security context and authenticated user. Used for authorization
 * checks to prevent IDOR vulnerabilities.
 */
@Service
@RequiredArgsConstructor
public class SecurityContextService {

    private final UserRepository userRepository;

    /**
     * Get the currently authenticated user.
     *
     * @return Optional containing the User if authenticated, empty otherwise
     */
    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        String username;

        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            username = (String) principal;
        } else {
            return Optional.empty();
        }

        // Skip anonymous user
        if ("anonymousUser".equals(username)) {
            return Optional.empty();
        }

        return userRepository.findByUsername(username);
    }

    /**
     * Get the current user ID.
     *
     * @return Optional containing the user ID if authenticated
     */
    public Optional<Long> getCurrentUserId() {
        return getCurrentUser().map(User::getId);
    }

    /**
     * Get the current username.
     *
     * @return Optional containing the username if authenticated
     */
    public Optional<String> getCurrentUsername() {
        return getCurrentUser().map(User::getUsername);
    }

    /**
     * Check if the current user is an admin.
     *
     * @return true if the current user has ROLE_ADMIN
     */
    public boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }
}

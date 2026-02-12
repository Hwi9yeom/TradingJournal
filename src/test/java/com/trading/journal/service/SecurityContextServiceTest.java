package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.trading.journal.entity.User;
import com.trading.journal.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityContextService 테스트")
class SecurityContextServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private SecurityContextService securityContextService;

    @Mock private SecurityContext securityContext;

    @Mock private Authentication authentication;

    @Nested
    @DisplayName("getCurrentUser 메서드")
    class GetCurrentUserTests {

        @Test
        @DisplayName("UserDetails principal로 인증된 경우 사용자를 반환한다")
        void shouldReturnUserWhenAuthenticatedWithUserDetailsPrincipal() {
            // Given
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("testuser");

            User expectedUser =
                    User.builder()
                            .id(1L)
                            .username("testuser")
                            .role("ROLE_USER")
                            .enabled(true)
                            .build();

            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(expectedUser));

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<User> result = securityContextService.getCurrentUser();

                // Then
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(expectedUser);
                assertThat(result.get().getUsername()).isEqualTo("testuser");
            }

            verify(userRepository).findByUsername("testuser");
        }

        @Test
        @DisplayName("String principal로 인증된 경우 사용자를 반환한다")
        void shouldReturnUserWhenAuthenticatedWithStringPrincipal() {
            // Given
            String username = "testuser";
            User expectedUser =
                    User.builder()
                            .id(2L)
                            .username(username)
                            .role("ROLE_USER")
                            .enabled(true)
                            .build();

            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(username);
            when(userRepository.findByUsername(username)).thenReturn(Optional.of(expectedUser));

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<User> result = securityContextService.getCurrentUser();

                // Then
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(expectedUser);
                assertThat(result.get().getId()).isEqualTo(2L);
            }

            verify(userRepository).findByUsername(username);
        }

        @Test
        @DisplayName("인증 정보가 null인 경우 빈 Optional을 반환한다")
        void shouldReturnEmptyWhenAuthenticationIsNull() {
            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(null);

                Optional<User> result = securityContextService.getCurrentUser();

                // Then
                assertThat(result).isEmpty();
            }

            verify(userRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("인증되지 않은 경우 빈 Optional을 반환한다")
        void shouldReturnEmptyWhenNotAuthenticated() {
            // Given
            when(authentication.isAuthenticated()).thenReturn(false);

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<User> result = securityContextService.getCurrentUser();

                // Then
                assertThat(result).isEmpty();
            }

            verify(userRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("익명 사용자인 경우 빈 Optional을 반환한다")
        void shouldReturnEmptyForAnonymousUser() {
            // Given
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn("anonymousUser");

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<User> result = securityContextService.getCurrentUser();

                // Then
                assertThat(result).isEmpty();
            }

            verify(userRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("알 수 없는 principal 타입인 경우 빈 Optional을 반환한다")
        void shouldReturnEmptyForUnknownPrincipalType() {
            // Given
            Object unknownPrincipal = new Object();
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(unknownPrincipal);

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<User> result = securityContextService.getCurrentUser();

                // Then
                assertThat(result).isEmpty();
            }

            verify(userRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("사용자가 데이터베이스에 없는 경우 빈 Optional을 반환한다")
        void shouldReturnEmptyWhenUserNotFoundInDatabase() {
            // Given
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("nonexistent");

            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<User> result = securityContextService.getCurrentUser();

                // Then
                assertThat(result).isEmpty();
            }

            verify(userRepository).findByUsername("nonexistent");
        }
    }

    @Nested
    @DisplayName("getCurrentUserId 메서드")
    class GetCurrentUserIdTests {

        @Test
        @DisplayName("인증된 경우 사용자 ID를 반환한다")
        void shouldReturnUserIdWhenAuthenticated() {
            // Given
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("testuser");

            User user =
                    User.builder()
                            .id(100L)
                            .username("testuser")
                            .role("ROLE_USER")
                            .enabled(true)
                            .build();

            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<Long> result = securityContextService.getCurrentUserId();

                // Then
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo(100L);
            }
        }

        @Test
        @DisplayName("인증되지 않은 경우 빈 Optional을 반환한다")
        void shouldReturnEmptyWhenNotAuthenticated() {
            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(null);

                Optional<Long> result = securityContextService.getCurrentUserId();

                // Then
                assertThat(result).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("getCurrentUsername 메서드")
    class GetCurrentUsernameTests {

        @Test
        @DisplayName("인증된 경우 사용자명을 반환한다")
        void shouldReturnUsernameWhenAuthenticated() {
            // Given
            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.getUsername()).thenReturn("testuser");

            User user =
                    User.builder()
                            .id(1L)
                            .username("testuser")
                            .role("ROLE_USER")
                            .enabled(true)
                            .build();

            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                Optional<String> result = securityContextService.getCurrentUsername();

                // Then
                assertThat(result).isPresent();
                assertThat(result.get()).isEqualTo("testuser");
            }
        }

        @Test
        @DisplayName("인증되지 않은 경우 빈 Optional을 반환한다")
        void shouldReturnEmptyWhenNotAuthenticated() {
            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(null);

                Optional<String> result = securityContextService.getCurrentUsername();

                // Then
                assertThat(result).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("isCurrentUserAdmin 메서드")
    class IsCurrentUserAdminTests {

        @Test
        @DisplayName("ROLE_ADMIN 권한이 있는 경우 true를 반환한다")
        void shouldReturnTrueForAdminRole() {
            // Given
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            when(authentication.getAuthorities()).thenReturn((List) authorities);

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                boolean result = securityContextService.isCurrentUserAdmin();

                // Then
                assertThat(result).isTrue();
            }
        }

        @Test
        @DisplayName("ROLE_USER 권한만 있는 경우 false를 반환한다")
        void shouldReturnFalseForUserRole() {
            // Given
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
            when(authentication.getAuthorities()).thenReturn((List) authorities);

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                boolean result = securityContextService.isCurrentUserAdmin();

                // Then
                assertThat(result).isFalse();
            }
        }

        @Test
        @DisplayName("여러 권한 중 ROLE_ADMIN이 포함된 경우 true를 반환한다")
        void shouldReturnTrueWhenAdminRoleIncludedAmongMultipleRoles() {
            // Given
            List<GrantedAuthority> authorities =
                    List.of(
                            new SimpleGrantedAuthority("ROLE_USER"),
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_MODERATOR"));
            when(authentication.getAuthorities()).thenReturn((List) authorities);

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                boolean result = securityContextService.isCurrentUserAdmin();

                // Then
                assertThat(result).isTrue();
            }
        }

        @Test
        @DisplayName("인증 정보가 null인 경우 false를 반환한다")
        void shouldReturnFalseWhenNoAuthentication() {
            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(null);

                boolean result = securityContextService.isCurrentUserAdmin();

                // Then
                assertThat(result).isFalse();
            }
        }

        @Test
        @DisplayName("권한이 비어있는 경우 false를 반환한다")
        void shouldReturnFalseWhenNoAuthorities() {
            // Given
            when(authentication.getAuthorities()).thenReturn(List.of());

            // When
            try (MockedStatic<SecurityContextHolder> mockedStatic =
                    mockStatic(SecurityContextHolder.class)) {
                mockedStatic.when(SecurityContextHolder::getContext).thenReturn(securityContext);
                when(securityContext.getAuthentication()).thenReturn(authentication);

                boolean result = securityContextService.isCurrentUserAdmin();

                // Then
                assertThat(result).isFalse();
            }
        }
    }
}

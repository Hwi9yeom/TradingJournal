package com.trading.journal.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.WebRequest;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock private WebRequest webRequest;

    @Test
    void handleAuthenticationException_returnsUnauthorized() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/login");

        ResponseEntity<ErrorResponse> response =
                handler.handleAuthenticationException(
                        new BadCredentialsException("Bad credentials"), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getError()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getPath()).isEqualTo("/api/auth/login");
    }

    @Test
    void handleRuntimeException_stillReturnsInternalServerError() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/anything");

        ResponseEntity<ErrorResponse> response =
                handler.handleRuntimeException(new RuntimeException("boom"), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
    }
}

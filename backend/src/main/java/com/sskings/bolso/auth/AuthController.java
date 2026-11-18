package com.sskings.bolso.auth;

import com.sskings.bolso.auth.AuthDtos.AuthResponse;
import com.sskings.bolso.auth.AuthDtos.ChangePasswordRequest;
import com.sskings.bolso.auth.AuthDtos.LoginRequest;
import com.sskings.bolso.auth.AuthDtos.RegisterRequest;
import com.sskings.bolso.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @PostMapping("/api/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(req));
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    /** Encerra TODAS as sessões do usuário (a versão de token muda). */
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout() {
        auth.logout(CurrentUser.get());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/me/password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        return auth.changePassword(CurrentUser.get(), req);
    }
}

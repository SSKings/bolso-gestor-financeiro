package com.sskings.bolso.auth;

import com.sskings.bolso.common.PlainText;
import com.sskings.bolso.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    /** 8 a 72 caracteres (o BCrypt ignora o que passa de 72 bytes), com letra e número. */
    static final String PASSWORD_POLICY = "^(?=.*\\p{L})(?=.*\\d).{8,72}$";

    public record RegisterRequest(
            @NotBlank @Size(max = 100) @PlainText String name,
            @NotBlank @Email @Size(max = 150) @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "must use only ASCII characters") String email,
            @NotBlank @Pattern(regexp = PASSWORD_POLICY, message = "must have 8 to 72 characters, with letters and numbers") String password) {
        public RegisterRequest {
            email = normalizeEmail(email);
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 150) String email, @NotBlank @Size(max = 200) String password) {
        public LoginRequest {
            email = normalizeEmail(email);
        }
    }

    /** Antes da validação: "  Ana@X.com " e "ana@x.com" são a mesma conta. */
    static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_POLICY, message = "must have 8 to 72 characters, with letters and numbers") String newPassword) {}

    public record UserView(UUID id, String name, String email) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getName(), u.getEmail());
        }
    }

    public record AuthResponse(String token, UserView user) {}
}

package com.sskings.bolso.auth;

import com.sskings.bolso.auth.AuthDtos.AuthResponse;
import com.sskings.bolso.auth.AuthDtos.ChangePasswordRequest;
import com.sskings.bolso.auth.AuthDtos.LoginRequest;
import com.sskings.bolso.auth.AuthDtos.RegisterRequest;
import com.sskings.bolso.auth.AuthDtos.UserView;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.config.BolsoProperties;
import com.sskings.bolso.security.JwtService;
import com.sskings.bolso.security.PasswordStrength;
import com.sskings.bolso.user.User;
import com.sskings.bolso.user.UserRepository;
import com.sskings.bolso.workspace.WorkspaceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Cadastro, login, logout e troca de senha.
 * <ul>
 *   <li>Login com a mesma resposta (e o mesmo custo de BCrypt) para senha errada e e-mail inexistente.</li>
 *   <li>Bloqueio por conta: N falhas seguidas → 15 min; cada bloqueio seguido dobra (até 24 h); login certo zera.
 *       A linha do usuário fica travada durante a checagem (tentativas simultâneas não escapam da contagem).</li>
 *   <li>Logout e troca de senha revogam todos os tokens (versão de token).</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    static final String INVALID_LOGIN = "Invalid email or password";
    static final String LOCKED = "Too many failed attempts. Try again later";
    static final Duration MAX_LOCK = Duration.ofHours(24);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final WorkspaceFactory workspaceFactory;
    private final BolsoProperties props;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, WorkspaceFactory workspaceFactory,
                       BolsoProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.workspaceFactory = workspaceFactory;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-for-constant-time-0000");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email();
        String name = Texts.clean(req.name());
        PasswordStrength.problem(req.password(), name, email).ifPresent(p -> { throw ApiException.badRequest(p); });
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("Email already in use");
        User user = users.save(new User(name, email, encoder.encode(req.password()), clock.instant()));
        workspaceFactory.create("Pessoal", user);
        return new AuthResponse(jwt.generate(user), UserView.of(user));
    }

    @Transactional(noRollbackFor = ApiException.class)   // a falha contada precisa ser gravada mesmo com o 401
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailForUpdate(req.email()).orElse(null);
        if (user == null) {
            encoder.matches(req.password(), dummyHash);   // mesmo custo: o tempo não revela quem tem conta
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw ApiException.tooManyRequests(LOCKED);
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            registerFailure(user, now);
            throw ApiException.unauthorized(INVALID_LOGIN);
        }
        user.setFailedLogins(0);
        user.setLockCount(0);
        user.setLockedUntil(null);
        return new AuthResponse(jwt.generate(user), UserView.of(user));
    }

    private void registerFailure(User user, Instant now) {
        user.setFailedLogins(user.getFailedLogins() + 1);
        if (user.getFailedLogins() >= props.login().maxFailures()) {
            user.setLockCount(user.getLockCount() + 1);
            Duration lock = Duration.ofMinutes(props.login().lockMinutes()).multipliedBy(1L << Math.min(user.getLockCount() - 1, 10));
            user.setLockedUntil(now.plus(lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock));
            user.setFailedLogins(0);
            log.warn("Conta #{} bloqueada por {} min após {} falhas", user.getId(), lock.toMinutes(), props.login().maxFailures());
        }
    }

    @Transactional
    public void logout(User current) {
        User user = users.findById(current.getId()).orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        user.revokeTokens();
    }

    @Transactional
    public AuthResponse changePassword(User current, ChangePasswordRequest req) {
        User user = users.findById(current.getId()).orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) throw ApiException.badRequest("Invalid password");
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("New password must be different from the current one");
        }
        PasswordStrength.problem(req.newPassword(), user.getName(), user.getEmail()).ifPresent(p -> { throw ApiException.badRequest(p); });
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.revokeTokens();
        return new AuthResponse(jwt.generate(user), UserView.of(user));
    }
}

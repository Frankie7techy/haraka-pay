package io.github.frankie7techy.haraka.auth;

import io.github.frankie7techy.haraka.account.AccountView;
import io.github.frankie7techy.haraka.account.WalletAccount;
import io.github.frankie7techy.haraka.account.WalletAccountRepository;
import io.github.frankie7techy.haraka.common.MoneyPolicy;
import io.github.frankie7techy.haraka.common.exception.DuplicateEmailException;
import io.github.frankie7techy.haraka.common.exception.InvalidCredentialsException;
import io.github.frankie7techy.haraka.security.JwtService;
import io.github.frankie7techy.haraka.security.UserPrincipal;
import io.github.frankie7techy.haraka.user.AppUser;
import io.github.frankie7techy.haraka.user.AppUserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registration opens the caller's wallet in the same transaction; no user may exist
 * without one. Login is always answered with the same neutral failure so the endpoint
 * cannot be used to enumerate registered emails.
 */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final WalletAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MoneyPolicy moneyPolicy;

    public AuthService(AppUserRepository users, WalletAccountRepository accounts,
                       PasswordEncoder passwordEncoder, JwtService jwtService, MoneyPolicy moneyPolicy) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.moneyPolicy = moneyPolicy;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException("Email is already registered");
        }
        AppUser user = users.save(new AppUser(email, request.fullName(), request.password(), passwordEncoder));
        WalletAccount wallet = accounts.save(new WalletAccount(user.getId(), moneyPolicy.currency()));
        return new AuthResponse("Bearer", tokenFor(user), jwtService.ttlMinutes(),
                UserView.from(user), AccountView.from(wallet));
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().strip().toLowerCase();
        AppUser user = users.findByEmail(email)
                .orElseThrow(AuthService::neutralFailure);
        if (!user.passwordMatches(request.password(), passwordEncoder)) {
            throw neutralFailure();
        }
        WalletAccount wallet = accounts.findFirstByUserId(user.getId()).orElse(null);
        return new AuthResponse("Bearer", tokenFor(user), jwtService.ttlMinutes(),
                UserView.from(user), wallet == null ? null : AccountView.from(wallet));
    }

    private String tokenFor(AppUser user) {
        return jwtService.issueToken(new UserPrincipal(user.getId(), user.getEmail(), user.getRole()));
    }

    private static InvalidCredentialsException neutralFailure() {
        return new InvalidCredentialsException("Email or password is incorrect");
    }
}

package in.sentra.security;

import in.sentra.project.Project;
import in.sentra.project.ProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Email String email,
            // BCrypt only uses the first 72 bytes, so longer passwords would be silently truncated.
            @NotBlank @Size(min = 8, max = 72) String password,
            String displayName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record TokenResponse(String token, long expiresInMinutes) {
    }

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TransactionTemplate tx;

    public AuthController(AppUserRepository users, ProjectRepository projects, PasswordEncoder encoder,
                          JwtService jwt, TransactionTemplate tx) {
        this.users = users;
        this.projects = projects;
        this.encoder = encoder;
        this.jwt = jwt;
        this.tx = tx;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = normalize(req.email());
        try {
            AppUser user = tx.execute(status -> {
                AppUser u = new AppUser();
                u.setEmail(email);
                u.setPasswordHash(encoder.encode(req.password()));
                u.setDisplayName(req.displayName());
                u = users.save(u);

                Project p = new Project();
                p.setUserId(u.getId());
                p.setName("Default");
                p.setSlug("default-" + UUID.randomUUID().toString().substring(0, 8));
                projects.save(p);
                return u;
            });
            return new TokenResponse(jwt.issue(user.getId(), user.getEmail()), jwt.expiryMinutes());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByEmail(normalize(req.email())).orElse(null);
        // Same response for unknown email and wrong password, so accounts can't be enumerated.
        if (user == null || !encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new TokenResponse(jwt.issue(user.getId(), user.getEmail()), jwt.expiryMinutes());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}

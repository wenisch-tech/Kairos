package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.AppUser;
import tech.wenisch.kairos.entity.AuthProvider;
import tech.wenisch.kairos.entity.UserRole;
import tech.wenisch.kairos.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .build();
    }

    @Transactional
    public void updateLastLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    public AppUser createUser(String email, String password, UserRole role) {
        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .provider(AuthProvider.LOCAL)
                .createdAt(LocalDateTime.now())
                .build();
        return userRepository.save(user);
    }

    public AppUser createOidcUser(String email) {
        return syncOidcUser(email, true)
                .orElseThrow(() -> new IllegalStateException("OIDC user creation unexpectedly returned empty"));
    }

    @Transactional
    public Optional<AppUser> syncOidcUser(String email, boolean createUsers) {
        Optional<AppUser> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            user.setLastLoginAt(LocalDateTime.now());
            return Optional.of(userRepository.save(user));
        }
        if (!createUsers) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash("")
                .role(UserRole.USER)
                .provider(AuthProvider.OIDC)
                .createdAt(now)
                .lastLoginAt(now)
                .build();
        return Optional.of(userRepository.save(user));
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Transactional
    public Optional<AppUser> updateRole(Long id, UserRole role) {
        return userRepository.findById(id).map(user -> {
            user.setRole(role);
            return userRepository.save(user);
        });
    }

    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    public long countUsers() {
        return userRepository.count();
    }
}

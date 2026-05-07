package com.emailmanager.service;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.User;
import com.emailmanager.repository.EmailAccountRepository;
import com.emailmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;

    /**
     * Find existing user by googleId or create a new one.
     * On first login (new user), any email accounts with no owner are claimed by
     * this user — migration path for single-user installs upgrading to multi-user.
     */
    @Transactional
    public User findOrCreateUser(String googleId, String email, String name) {
        return userRepository.findByGoogleId(googleId).orElseGet(() -> {
            User user = new User();
            user.setGoogleId(googleId);
            user.setEmail(email);
            user.setName(name);
            user.setRole(User.Role.USER);
            user = userRepository.save(user);
            log.info("New user registered: {} (googleId={})", email, googleId);
            claimOwnerlessAccounts(user);
            return user;
        });
    }

    /** Promote or demote a user's role. Admin-only operation enforced at the controller level. */
    @Transactional
    public User updateRole(Long userId, User.Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRole(role);
        return userRepository.save(user);
    }

    // Assign any pre-existing ownerless accounts to the first user who signs in.
    private void claimOwnerlessAccounts(User user) {
        List<EmailAccount> ownerless = emailAccountRepository.findByOwnerIsNull();
        if (!ownerless.isEmpty()) {
            ownerless.forEach(a -> a.setOwner(user));
            emailAccountRepository.saveAll(ownerless);
            log.info("Assigned {} ownerless email account(s) to {}", ownerless.size(), user.getEmail());
        }
    }
}

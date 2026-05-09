package com.emailmanager.service;

import com.emailmanager.entity.User;
import com.emailmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
            return user;
        });
    }

    @Transactional
    public User updateRole(Long userId, User.Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRole(role);
        return userRepository.save(user);
    }
}

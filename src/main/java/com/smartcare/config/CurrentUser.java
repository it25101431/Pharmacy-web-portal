package com.smartcare.config;

import com.smartcare.model.User;
import com.smartcare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserRepository users;

    public User get() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByUsername(name).orElseThrow(() -> new IllegalStateException("Please log in again."));
    }
}

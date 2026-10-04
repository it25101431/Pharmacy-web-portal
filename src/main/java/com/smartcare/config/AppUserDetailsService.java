package com.smartcare.config;

import com.smartcare.model.User;
import com.smartcare.model.UserStatus;
import com.smartcare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

/** Loads users from MySQL. Only ACTIVE accounts (approved by an admin) can log in. */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
                .password(u.getPassword())
                .roles(u.getRole().name())
                .disabled(u.getStatus() != UserStatus.ACTIVE)
                .build();
    }
}

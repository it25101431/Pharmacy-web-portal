package com.smartcare.service;

import com.smartcare.model.*;
import com.smartcare.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepository users;

    public void send(User user, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message.length() > 250 ? message.substring(0, 250) : message);
        repo.save(n);
    }

    public void sendToRole(Role role, String message) {
        users.findByRoleAndStatus(role, UserStatus.ACTIVE).forEach(u -> send(u, message));
    }
}


package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository repo;
    private final CurrentUser current;

    @GetMapping("/notifications")
    public String list(Model m) {
        List<Notification> list = repo.findByUserOrderByCreatedAtDesc(current.get());
        m.addAttribute("items", list);
        return "notifications";
    }

    @PostMapping("/notifications/read")
    public String markRead() {
        List<Notification> list = repo.findByUserOrderByCreatedAtDesc(current.get());
        list.forEach(n -> n.setSeen(true));
        repo.saveAll(list);
        return "redirect:/notifications";
    }
}


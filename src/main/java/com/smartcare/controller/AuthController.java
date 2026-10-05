package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
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
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final NotificationService notes;

    @Getter @Setter
    public static class RegisterForm {
        @NotBlank @Size(min = 4, max = 30) @Pattern(regexp = "^[A-Za-z0-9_.]*$", message = "Letters, digits, . and _ only")
        private String username;
        @NotBlank @Size(min = 6, max = 60, message = "Password must be 6-60 characters")
        private String password;
        @NotBlank @Size(max = 100)
        private String fullName;
        @NotBlank @Email
        private String email;
        @NotBlank @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "Enter a valid phone number")
        private String phone;
        @Size(max = 250)
        private String address;
        @NotNull
        private Role role = Role.CUSTOMER;
    }

    @GetMapping("/")
    public String root() { return "redirect:/home"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/home")
    public String home(Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority();
        return "redirect:" + switch (role) {
            case "ROLE_ADMIN" -> "/admin/users";
            case "ROLE_PHARMACIST" -> "/pharmacist/medicines";
            case "ROLE_CUSTOMER" -> "/customer/medicines";
            case "ROLE_SUPPLIER" -> "/supplier/orders";
            case "ROLE_DELIVERY_STAFF" -> "/delivery/deliveries";
            case "ROLE_STORE_MANAGER" -> "/manager/dashboard";
            default -> "/login";
        };
    }

    private static final List<Role> SELF_REGISTER = List.of(Role.CUSTOMER, Role.SUPPLIER, Role.DELIVERY_STAFF, Role.PHARMACIST, Role.STORE_MANAGER);

    @GetMapping("/register")
    public String registerForm(Model m) {
        m.addAttribute("form", new RegisterForm());
        m.addAttribute("roles", SELF_REGISTER);
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm f, BindingResult br, Model m, RedirectAttributes ra) {
        m.addAttribute("roles", SELF_REGISTER);
        if (f.getRole() == null || !SELF_REGISTER.contains(f.getRole())) br.rejectValue("role", "invalid", "Invalid role");
        if (f.getUsername() != null && users.existsByUsername(f.getUsername())) br.rejectValue("username", "taken", "Username is already taken");
        if (br.hasErrors()) return "register";
        User u = new User();
        u.setUsername(f.getUsername());
        u.setPassword(encoder.encode(f.getPassword()));
        u.setFullName(f.getFullName().trim());
        u.setEmail(f.getEmail().trim());
        u.setPhone(f.getPhone().trim());
        u.setAddress(f.getAddress() == null ? "" : f.getAddress().trim());
        u.setRole(f.getRole());
        boolean auto = f.getRole() == Role.CUSTOMER;
        u.setStatus(auto ? UserStatus.ACTIVE : UserStatus.PENDING);
        users.save(u);
        if (!auto) notes.sendToRole(Role.ADMIN, "New " + f.getRole() + " registration awaiting approval: " + u.getUsername());
        ra.addFlashAttribute("msg", auto ? "Account created. You can log in now."
                : "Registration submitted. An administrator must approve it before you can log in.");
        return "redirect:/login";
    }
}

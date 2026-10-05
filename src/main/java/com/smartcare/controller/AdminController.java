package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import java.nio.charset.StandardCharsets;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final OrderRepository orders;
    private final MedicineRepository medicines;
    private final OrderService orderService;
    private final NotificationService notes;
    private final UserDeletionService deletion;
    private final CurrentUser current;

    // ---------- Users & roles ----------
    @GetMapping("/users")
    public String list(Model m) {
        List<User> all = users.findAllByOrderByCreatedAtDesc();
        User me = current.get();
        // Work out once which accounts may be deleted, so the page only offers a
        // Delete button where it will actually succeed.
        Set<Long> deletable = all.stream()
                .filter(u -> deletion.canDelete(u, me))
                .map(User::getId)
                .collect(Collectors.toSet());
        m.addAttribute("users", all);
        m.addAttribute("roles", Role.values());
        m.addAttribute("meId", me.getId());
        m.addAttribute("deletable", deletable);
        return "admin/users";
    }

    /**
     * DELETE - permanently removes an account that has no history. An account
     * that has orders, prescriptions, purchase orders or deliveries is refused
     * with a reason, and the admin is told to deactivate it instead.
     */
    @PostMapping("/users/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        String username = find(id).getUsername();
        deletion.delete(id, current.get());
        ra.addFlashAttribute("msg", username + " was deleted permanently.");
        return "redirect:/admin/users";
    }

    @GetMapping("/users/new")
    public String newForm(Model m) {
        m.addAttribute("u", new User());
        m.addAttribute("roles", Role.values());
        return "admin/user-form";
    }

    @GetMapping("/users/{id}/edit")
    public String editForm(@PathVariable Long id, Model m) {
        m.addAttribute("u", find(id));
        m.addAttribute("roles", Role.values());
        return "admin/user-form";
    }

    @PostMapping("/users/save")
    public String save(@RequestParam(required = false) Long id, @RequestParam String username,
                       @RequestParam(defaultValue = "") String password, @RequestParam String fullName,
                       @RequestParam String email, @RequestParam(defaultValue = "") String phone,
                       @RequestParam(defaultValue = "") String address, @RequestParam Role role, RedirectAttributes ra) {
        username = username.trim();
        if (!username.matches("^[A-Za-z0-9_.]{4,30}$")) throw new IllegalStateException("Username must be 4-30 letters, digits, '.' or '_'.");
        if (fullName.isBlank() || !email.contains("@")) throw new IllegalStateException("Full name and a valid email are required.");
        if (!phone.isBlank() && !phone.matches("^[0-9+\\- ]{7,15}$")) throw new IllegalStateException("Enter a valid phone number.");
        User u;
        if (id == null) {
            if (users.existsByUsername(username)) throw new IllegalStateException("Username is already taken.");
            if (password.length() < 6) throw new IllegalStateException("Password must be at least 6 characters.");
            u = new User();
            u.setUsername(username);
            u.setPassword(encoder.encode(password));
            u.setStatus(UserStatus.ACTIVE);
        } else {
            u = find(id);
            if (id.equals(current.get().getId()) && role != u.getRole()) throw new IllegalStateException("You cannot change your own role.");
            if (!password.isBlank()) {
                if (password.length() < 6) throw new IllegalStateException("Password must be at least 6 characters.");
                u.setPassword(encoder.encode(password));
            }
        }
        u.setFullName(fullName.trim()); u.setEmail(email.trim()); u.setPhone(phone.trim()); u.setAddress(address.trim());
        u.setRole(role);
        users.save(u);
        ra.addFlashAttribute("msg", "Account " + u.getUsername() + " saved.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam Role role, RedirectAttributes ra) {
        User u = find(id);
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        users.save(u);
        notes.send(u, "Your account was approved as " + role + ".");
        ra.addFlashAttribute("msg", u.getUsername() + " approved as " + role + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes ra) {
        if (id.equals(current.get().getId())) throw new IllegalStateException("You cannot reject your own account.");
        User u = find(id);
        u.setStatus(UserStatus.REJECTED);
        users.save(u);
        ra.addFlashAttribute("msg", u.getUsername() + " rejected.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/status")
    public String status(@PathVariable Long id, @RequestParam UserStatus status, RedirectAttributes ra) {
        if (id.equals(current.get().getId())) throw new IllegalStateException("You cannot change your own account status.");
        if (status != UserStatus.ACTIVE && status != UserStatus.DEACTIVATED) throw new IllegalStateException("Invalid status.");
        User u = find(id);
        u.setStatus(status);
        users.save(u);
        ra.addFlashAttribute("msg", u.getUsername() + " is now " + status + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String role(@PathVariable Long id, @RequestParam Role role, RedirectAttributes ra) {
        if (id.equals(current.get().getId())) throw new IllegalStateException("You cannot change your own role.");
        User u = find(id);
        u.setRole(role);
        users.save(u);
        ra.addFlashAttribute("msg", u.getUsername() + " is now " + role + ".");
        return "redirect:/admin/users";
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalStateException("User not found."));
    }

    // ---------- Orders & delivery assignment ----------
    @GetMapping("/orders")
    public String orderList(Model m) {
        m.addAttribute("items", orders.findAllByOrderByCreatedAtDesc());
        m.addAttribute("staff", users.findByRoleAndStatus(Role.DELIVERY_STAFF, UserStatus.ACTIVE));
        return "admin/orders";
    }

    @PostMapping("/orders/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam Long staffId, RedirectAttributes ra) {
        orderService.assignDelivery(id, staffId);
        ra.addFlashAttribute("msg", "Delivery assigned for order #" + id + ".");
        return "redirect:/admin/orders";
    }

    // ---------- Reports ----------
    private List<CustomerOrder> sales(LocalDate from, LocalDate to) {
        return orders.findByCreatedAtBetweenAndStatusNot(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), OrderStatus.REJECTED);
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Model m) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.withDayOfMonth(1);
        List<CustomerOrder> list = sales(f, t);
        BigDecimal revenue = list.stream().map(CustomerOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Integer> sold = new HashMap<>();
        list.forEach(o -> o.getItems().forEach(i -> sold.merge(i.getMedicine().getName(), i.getQuantity(), Integer::sum)));
        Map<String, Integer> top = sold.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        List<Medicine> inv = medicines.findByActiveTrueOrderByNameAsc();
        BigDecimal stockValue = inv.stream().map(x -> x.getPrice().multiply(BigDecimal.valueOf(x.getStock()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        m.addAttribute("from", f); m.addAttribute("to", t);
        m.addAttribute("orders", list); m.addAttribute("revenue", revenue);
        m.addAttribute("rxOrders", list.stream().filter(o -> o.getPrescription() != null).count());
        m.addAttribute("top", top);
        m.addAttribute("inventory", inv); m.addAttribute("stockValue", stockValue);
        return "admin/reports";
    }

    @GetMapping("/reports/sales.csv")
    public ResponseEntity<byte[]> salesCsv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        StringBuilder sb = new StringBuilder("Order ID,Date,Customer,Status,Payment Method,Payment Status,Total (LKR)\n");
        for (CustomerOrder o : sales(from, to))
            sb.append(o.getId()).append(',').append(o.getCreatedAt().toLocalDate()).append(',').append(q(o.getCustomer().getFullName()))
              .append(',').append(o.getStatus()).append(',').append(o.getPaymentMethod()).append(',').append(o.getPaymentStatus())
              .append(',').append(o.getTotal()).append('\n');
        return csv("sales-report.csv", sb);
    }

    @GetMapping("/reports/inventory.csv")
    public ResponseEntity<byte[]> inventoryCsv() {
        StringBuilder sb = new StringBuilder("ID,Name,Category,Batch,Expiry,Stock,Reorder Level,Price (LKR),Prescription Required\n");
        for (Medicine x : medicines.findByActiveTrueOrderByNameAsc())
            sb.append(x.getId()).append(',').append(q(x.getName())).append(',').append(q(x.getCategory())).append(',').append(q(x.getBatchNo()))
              .append(',').append(x.getExpiryDate()).append(',').append(x.getStock()).append(',').append(x.getReorderLevel())
              .append(',').append(x.getPrice()).append(',').append(x.isPrescriptionRequired()).append('\n');
        return csv("inventory-report.csv", sb);
    }

    private ResponseEntity<byte[]> csv(String name, StringBuilder sb) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String q(String s) { return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\""; }
}

package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/delivery")
@RequiredArgsConstructor
public class DeliveryController {
    private final OrderRepository orders;
    private final OrderService orderService;
    private final DeliveryNoteRepository deliveryNotes;
    private final CurrentUser current;

    @GetMapping("/deliveries")
    public String list(Model m) {
        m.addAttribute("items", orders.findByDeliveryStaffOrderByCreatedAtDesc(current.get()));
        return "delivery/deliveries";
    }

    @PostMapping("/deliveries/{id}/status")
    public String status(@PathVariable Long id, @RequestParam DeliveryStatus status, RedirectAttributes ra) {
        orderService.updateDelivery(id, current.get(), status);
        ra.addFlashAttribute("msg", "Order #" + id + " updated.");
        return "redirect:/delivery/deliveries";
    }

    @PostMapping("/deliveries/{id}/confirm")
    public String confirm(@PathVariable Long id, @RequestParam String otp, RedirectAttributes ra) {
        orderService.confirmDelivery(id, current.get(), otp);
        ra.addFlashAttribute("msg", "Order #" + id + " delivered and confirmed.");
        return "redirect:/delivery/deliveries";
    }

    @PostMapping("/deliveries/{id}/issue")
    public String issue(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        orderService.reportIssue(id, current.get(), reason);
        ra.addFlashAttribute("msg", "Issue reported for order #" + id + ". Admin and customer were notified.");
        return "redirect:/delivery/deliveries";
    }

    // ------------------------------------------------------------------
    // Delivery notes - the courier's own log against an order they carry.
    // This is the Delivery module's CRUD: create, read, update, delete.
    // A courier may only touch notes they wrote themselves.
    // ------------------------------------------------------------------

    @GetMapping("/notes")
    public String notes(Model m) {
        m.addAttribute("items", deliveryNotes.findByStaffOrderByIdDesc(current.get()));
        return "delivery/notes";
    }

    @GetMapping("/notes/new")
    public String newNote(Model m) {
        DeliveryNote n = new DeliveryNote();
        m.addAttribute("note", n);
        m.addAttribute("myOrders", orders.findByDeliveryStaffOrderByCreatedAtDesc(current.get()));
        return "delivery/note-form";
    }

    @GetMapping("/notes/{id}/edit")
    public String editNote(@PathVariable Long id, Model m) {
        m.addAttribute("note", ownNote(id));
        m.addAttribute("myOrders", orders.findByDeliveryStaffOrderByCreatedAtDesc(current.get()));
        return "delivery/note-form";
    }

    @PostMapping("/notes")
    public String createNote(@Valid @ModelAttribute("note") DeliveryNote form, BindingResult br,
                             @RequestParam Long orderId, Model m, RedirectAttributes ra) {
        CustomerOrder o = assignedOrder(orderId);
        if (br.hasErrors()) {
            m.addAttribute("myOrders", orders.findByDeliveryStaffOrderByCreatedAtDesc(current.get()));
            return "delivery/note-form";
        }
        DeliveryNote n = new DeliveryNote();
        n.setOrder(o);
        n.setStaff(current.get());
        n.setType(form.getType());
        n.setNote(form.getNote().trim());
        deliveryNotes.save(n);
        ra.addFlashAttribute("msg", "Note added for order #" + o.getId() + ".");
        return "redirect:/delivery/notes";
    }

    @PostMapping("/notes/{id}")
    public String updateNote(@PathVariable Long id, @Valid @ModelAttribute("note") DeliveryNote form,
                             BindingResult br, @RequestParam Long orderId, Model m, RedirectAttributes ra) {
        DeliveryNote n = ownNote(id);
        CustomerOrder o = assignedOrder(orderId);
        if (br.hasErrors()) {
            form.setId(id);
            m.addAttribute("myOrders", orders.findByDeliveryStaffOrderByCreatedAtDesc(current.get()));
            return "delivery/note-form";
        }
        n.setOrder(o);
        n.setType(form.getType());
        n.setNote(form.getNote().trim());
        n.setUpdatedAt(LocalDateTime.now());
        deliveryNotes.save(n);
        ra.addFlashAttribute("msg", "Note updated.");
        return "redirect:/delivery/notes";
    }

    @PostMapping("/notes/{id}/delete")
    public String deleteNote(@PathVariable Long id, RedirectAttributes ra) {
        deliveryNotes.delete(ownNote(id));
        ra.addFlashAttribute("msg", "Note deleted.");
        return "redirect:/delivery/notes";
    }

    /** A courier can only read or change their own notes. */
    private DeliveryNote ownNote(Long id) {
        DeliveryNote n = deliveryNotes.findById(id)
                .orElseThrow(() -> new IllegalStateException("Note not found."));
        if (!n.getStaff().getId().equals(current.get().getId()))
            throw new IllegalStateException("That note belongs to another courier.");
        return n;
    }

    /** A note can only be attached to an order actually assigned to this courier. */
    private CustomerOrder assignedOrder(Long orderId) {
        CustomerOrder o = orders.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found."));
        if (o.getDeliveryStaff() == null || !o.getDeliveryStaff().getId().equals(current.get().getId()))
            throw new IllegalStateException("That order is not assigned to you.");
        return o;
    }
}
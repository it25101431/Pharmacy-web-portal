package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/supplier")
@RequiredArgsConstructor
public class SupplierController {
    private final PurchaseOrderRepository pos;
    private final SupplierOfferRepository offers;
    private final SupplyService supply;
    private final CurrentUser current;

    // ---------------- Purchase orders (respond to the pharmacy) ----------------

    @GetMapping("/orders")
    public String list(Model m) {
        m.addAttribute("items", pos.findBySupplierOrderByCreatedAtDesc(current.get()));
        return "supplier/orders";
    }

    @PostMapping("/orders/{id}/availability")
    public String availability(@PathVariable Long id, @RequestParam PoStatus status, RedirectAttributes ra) {
        supply.setAvailability(id, current.get(), status);
        ra.addFlashAttribute("msg", "PO #" + id + " marked as " + status + ".");
        return "redirect:/supplier/orders";
    }

    @PostMapping("/orders/{id}/deliver")
    public String deliver(@PathVariable Long id, RedirectAttributes ra) {
        supply.deliver(id, current.get());
        ra.addFlashAttribute("msg", "Shipment for PO #" + id + " confirmed as delivered.");
        return "redirect:/supplier/orders";
    }

    // ---------------- Product offers: the supplier's own CRUD module ----------------

    /** READ - list every offer this supplier owns. */
    @GetMapping("/offers")
    public String offers(Model m) {
        m.addAttribute("items", offers.findBySupplierOrderByIdDesc(current.get()));
        return "supplier/offers";
    }

    /** CREATE - blank form. */
    @GetMapping("/offers/new")
    public String newOffer(Model m) {
        m.addAttribute("offer", new SupplierOffer());
        return "supplier/offer-form";
    }

    /** UPDATE - form pre-filled with an existing offer. */
    @GetMapping("/offers/{id}/edit")
    public String editOffer(@PathVariable Long id, Model m) {
        m.addAttribute("offer", own(id));
        return "supplier/offer-form";
    }

    /** CREATE - save a new offer. */
    @PostMapping("/offers")
    public String createOffer(@Valid @ModelAttribute("offer") SupplierOffer form,
                              BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) return "supplier/offer-form";
        User me = current.get();
        if (offers.existsBySupplierAndProductNameIgnoreCase(me, form.getProductName().trim())) {
            br.rejectValue("productName", "dup", "You already list a product with this name.");
            return "supplier/offer-form";
        }
        SupplierOffer o = new SupplierOffer();
        o.setSupplier(me);
        copy(o, form);
        offers.save(o);
        ra.addFlashAttribute("msg", "Offer \"" + o.getProductName() + "\" added to your catalogue.");
        return "redirect:/supplier/offers";
    }

    /** UPDATE - save changes to an existing offer. */
    @PostMapping("/offers/{id}")
    public String updateOffer(@PathVariable Long id, @Valid @ModelAttribute("offer") SupplierOffer form,
                              BindingResult br, RedirectAttributes ra) {
        SupplierOffer o = own(id);           // ownership checked before anything is changed
        form.setId(id);
        if (br.hasErrors()) return "supplier/offer-form";
        // The create path blocks duplicate names, so the edit path must too:
        // otherwise a second product could simply be renamed onto an existing one.
        if (offers.existsBySupplierAndProductNameIgnoreCaseAndIdNot(
                current.get(), form.getProductName().trim(), id)) {
            br.rejectValue("productName", "dup", "You already list a product with this name.");
            return "supplier/offer-form";
        }
        copy(o, form);
        o.setUpdatedAt(LocalDateTime.now());
        offers.save(o);
        ra.addFlashAttribute("msg", "Offer \"" + o.getProductName() + "\" updated.");
        return "redirect:/supplier/offers";
    }

    /** DELETE - remove an offer from the catalogue permanently. */
    @PostMapping("/offers/{id}/delete")
    public String deleteOffer(@PathVariable Long id, RedirectAttributes ra) {
        SupplierOffer o = own(id);
        offers.delete(o);
        ra.addFlashAttribute("msg", "Offer \"" + o.getProductName() + "\" deleted.");
        return "redirect:/supplier/offers";
    }

    /** Quick availability toggle without opening the full edit form. */
    @PostMapping("/offers/{id}/toggle")
    public String toggleOffer(@PathVariable Long id, RedirectAttributes ra) {
        SupplierOffer o = own(id);
        o.setAvailable(!o.isAvailable());
        o.setUpdatedAt(LocalDateTime.now());
        offers.save(o);
        ra.addFlashAttribute("msg", "\"" + o.getProductName() + "\" is now "
                + (o.isAvailable() ? "available" : "unavailable") + ".");
        return "redirect:/supplier/offers";
    }

    private void copy(SupplierOffer target, SupplierOffer form) {
        target.setProductName(form.getProductName().trim());
        target.setGenericName(form.getGenericName());
        target.setManufacturer(form.getManufacturer());
        target.setUnitPrice(form.getUnitPrice());
        target.setMinOrderQty(form.getMinOrderQty());
        target.setLeadTimeDays(form.getLeadTimeDays());
        target.setAvailable(form.isAvailable());
        target.setPriceValidUntil(form.getPriceValidUntil());
        target.setNotes(form.getNotes());
    }

    /** Loads an offer and refuses it if it belongs to a different supplier. */
    private SupplierOffer own(Long id) {
        SupplierOffer o = offers.findById(id)
                .orElseThrow(() -> new IllegalStateException("Offer not found."));
        if (!o.getSupplier().getId().equals(current.get().getId()))
            throw new IllegalStateException("That offer belongs to another supplier.");
        return o;
    }
}

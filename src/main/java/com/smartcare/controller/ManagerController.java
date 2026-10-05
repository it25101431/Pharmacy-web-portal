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
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {
    private final OrderRepository orders;
    private final MedicineRepository medicines;
    private final PurchaseOrderRepository pos;
    private final UserRepository users;
    private final SupplyService supply;
    private final SupplierOfferRepository offers;

    @GetMapping("/dashboard")
    public String dashboard(Model m) {
        List<CustomerOrder> valid = orders.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() != OrderStatus.REJECTED).collect(Collectors.toList());
        LocalDate today = LocalDate.now();
        BigDecimal todayRevenue = BigDecimal.ZERO, totalRevenue = BigDecimal.ZERO;
        BigDecimal todayOrderValue = BigDecimal.ZERO, totalOrderValue = BigDecimal.ZERO;
        long todayOrders = 0;
        for (CustomerOrder o : valid) {
            boolean paid = o.getPaymentStatus() == PaymentStatus.PAID;
            // Revenue counts money actually collected. Cash-on-delivery orders stay
            // PENDING until the courier confirms delivery, so counting them as revenue
            // would overstate income for orders that may never be paid.
            totalOrderValue = totalOrderValue.add(o.getTotal());
            if (paid) totalRevenue = totalRevenue.add(o.getTotal());
            if (o.getCreatedAt().toLocalDate().equals(today)) {
                todayOrderValue = todayOrderValue.add(o.getTotal());
                if (paid) todayRevenue = todayRevenue.add(o.getTotal());
                todayOrders++;
            }
        }
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            labels.add(d.getDayOfMonth() + "/" + d.getMonthValue());
            values.add(valid.stream()
                    .filter(o -> o.getCreatedAt().toLocalDate().equals(d))
                    .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                    .map(CustomerOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s, 0L);
        orders.findAll().forEach(o -> byStatus.merge(o.getStatus(), 1L, Long::sum));
        m.addAttribute("todayRevenue", todayRevenue); m.addAttribute("totalRevenue", totalRevenue);
        m.addAttribute("todayOrderValue", todayOrderValue); m.addAttribute("totalOrderValue", totalOrderValue);
        m.addAttribute("todayOrders", todayOrders); m.addAttribute("totalOrders", valid.size());
        m.addAttribute("lowStockCount", medicines.lowStock().size());
        m.addAttribute("openPos", pos.findAll().stream().filter(p -> p.getStatus() != PoStatus.DELIVERED).count());
        m.addAttribute("labels", labels); m.addAttribute("values", values); m.addAttribute("byStatus", byStatus);
        return "manager/dashboard";
    }

    @GetMapping("/alerts")
    public String alerts(Model m) {
        m.addAttribute("lowStock", medicines.lowStock());
        m.addAttribute("suppliers", users.findByRoleAndStatus(Role.SUPPLIER, UserStatus.ACTIVE));
        return "manager/alerts";
    }

    @PostMapping("/purchase-orders")
    public String createPo(@RequestParam Long supplierId, @RequestParam Long medicineId, @RequestParam int quantity, RedirectAttributes ra) {
        PurchaseOrder po = supply.create(supplierId, medicineId, quantity);
        ra.addFlashAttribute("msg", "Purchase order #" + po.getId() + " sent to " + po.getSupplier().getFullName() + ".");
        return "redirect:/manager/purchase-orders";
    }

    @GetMapping("/purchase-orders")
    public String poList(Model m) {
        m.addAttribute("items", pos.findAllByOrderByCreatedAtDesc());
        m.addAttribute("payments", SupplierPayment.values());
        return "manager/purchase-orders";
    }

    @PostMapping("/purchase-orders/{id}/payment")
    public String payment(@PathVariable Long id, @RequestParam SupplierPayment payment, RedirectAttributes ra) {
        supply.setPayment(id, payment);
        ra.addFlashAttribute("msg", "Payment status of PO #" + id + " set to " + payment + ".");
        return "redirect:/manager/purchase-orders";
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public String cancelPo(@PathVariable Long id, RedirectAttributes ra) {
        supply.cancel(id);
        ra.addFlashAttribute("msg", "Purchase order #" + id + " cancelled.");
        return "redirect:/manager/purchase-orders";
    }

    @Getter @AllArgsConstructor
    public static class SupplierStat {
        private final User supplier;
        private final long total, delivered, onTime, outOfStock;
        public long getRate() { return delivered == 0 ? 0 : Math.round(onTime * 100.0 / delivered); }
    }

    /**
     * What the suppliers say they can currently provide. The supplier catalogue
     * screen tells suppliers the store manager sees their available products, so
     * this is the screen that makes that true. Only offers marked available are
     * listed: an unavailable one cannot be ordered today.
     */
    @GetMapping("/supplier-offers")
    public String supplierOffers(@RequestParam(required = false) String q, Model m) {
        List<SupplierOffer> items = offers.findByAvailableTrueOrderByProductNameAsc();
        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase();
            items = items.stream()
                    .filter(o -> o.getProductName().toLowerCase().contains(needle)
                            || o.getSupplier().getFullName().toLowerCase().contains(needle))
                    .toList();
        }
        m.addAttribute("items", items);
        m.addAttribute("q", q);
        return "manager/supplier-offers";
    }

    @GetMapping("/supplier-report")
    public String supplierReport(Model m) {
        List<PurchaseOrder> all = pos.findAll();
        List<SupplierStat> stats = new ArrayList<>();
        for (User s : users.findByRoleAndStatus(Role.SUPPLIER, UserStatus.ACTIVE)) {
            List<PurchaseOrder> mine = all.stream().filter(p -> p.getSupplier().getId().equals(s.getId())).collect(Collectors.toList());
            stats.add(new SupplierStat(s, mine.size(),
                    mine.stream().filter(p -> p.getStatus() == PoStatus.DELIVERED).count(),
                    mine.stream().filter(PurchaseOrder::isOnTime).count(),
                    mine.stream().filter(p -> p.getStatus() == PoStatus.OUT_OF_STOCK).count()));
        }
        m.addAttribute("stats", stats);
        return "manager/supplier-report";
    }
}


package com.smartcare.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shopping cart kept in the HTTP session (medicineId -> quantity). */
@Component
@SessionScope
public class CartService {
    private final Map<Long, Integer> items = new LinkedHashMap<>();

    public void add(Long id, int qty) { items.merge(id, qty, Integer::sum); }
    public void set(Long id, int qty) { if (qty <= 0) items.remove(id); else items.put(id, qty); }
    public void clear() { items.clear(); }
    public Map<Long, Integer> getItems() { return items; }
    public int count() { return items.values().stream().mapToInt(Integer::intValue).sum(); }
}

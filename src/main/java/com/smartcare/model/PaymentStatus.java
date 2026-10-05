package com.smartcare.model;

/** REFUNDED: a paid order was rejected (e.g. its prescription was rejected), so the payment is returned. */
public enum PaymentStatus { PENDING, PAID, REFUNDED }

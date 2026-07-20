package com.example.store.service;

import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;

@Service
public class SubscriptionService {

    @Value("${stripe.secret.key}")
    private String secretKey;

    // ─── CREATE SUBSCRIPTION CHECKOUT SESSION ─────────────────────
    public Map<String, String> createSubscriptionSession(
            Integer planId, String stripePriceId,
            String username, String email, Integer subId) throws Exception {

        Stripe.apiKey = secretKey;

        CustomerCreateParams customerParams = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(username)
                .putMetadata("username", username)
                .build();

        Customer stripeCustomer = Customer.create(customerParams);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(stripeCustomer.getId())
                
                .setSuccessUrl("http://localhost:8080/index.html?subscription=success&planId=" + planId + "&subId=" + subId)
                .setCancelUrl("http://localhost:8080/index.html?subscription=cancelled&planId=" + planId)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPrice(stripePriceId)
                                .build()
                )
                .build();

        Session session = Session.create(params);
        System.out.println("Subscription session created: " + session.getId());

        Map<String, String> result = new HashMap<>();
        result.put("sessionId",        session.getId());
        result.put("checkoutUrl",      session.getUrl());
        result.put("stripeCustomerId", stripeCustomer.getId());
        return result;
    }

    // ─── VERIFY SUBSCRIPTION PAYMENT ─────────────────────────────
    public Map<String, String> verifySubscription(String sessionId) throws Exception {
        Stripe.apiKey = secretKey;

        Session session = Session.retrieve(sessionId);

        Map<String, String> result = new HashMap<>();
        result.put("paymentStatus",    session.getPaymentStatus());
        result.put("subscriptionId",   session.getSubscription());
        result.put("stripeCustomerId", session.getCustomer());
        return result;
    }

    // ─── CANCEL SUBSCRIPTION ON STRIPE ───────────────────────────
    public void cancelSubscription(String stripeSubId) throws Exception {
        Stripe.apiKey = secretKey;
        com.stripe.model.Subscription subscription =
                com.stripe.model.Subscription.retrieve(stripeSubId);
        subscription.cancel();
        System.out.println(" Stripe subscription cancelled: " + stripeSubId);
    }
}
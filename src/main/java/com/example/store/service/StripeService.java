package com.example.store.service;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;

@Service
public class StripeService {

    @Value("${stripe.secret.key}")
    private String secretKey;

    //check out session
    public Map<String, String> createCheckoutSession(Integer orderId, double totalAmount, String username) throws Exception {
        Stripe.apiKey = secretKey;

        long amountInCents = Math.round(totalAmount * 100);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("http://localhost:8080/index.html?payment=success&orderId=" + orderId)
                .setCancelUrl("http://localhost:8080/index.html?payment=cancelled&orderId=" + orderId)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("NexStore Order #" + orderId)
                                                                .setDescription("Order placed by " + username)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        Session session = Session.create(params);
        System.out.println(" Stripe session created: " + session.getId());

        // Return both session ID and checkout URL
        Map<String, String> result = new HashMap<>();
        result.put("sessionId",   session.getId());
        result.put("checkoutUrl", session.getUrl());
        return result;
    }

    //verification of payment
    public boolean verifyPayment(String sessionId) throws Exception {
        Stripe.apiKey =  secretKey;
        Session session = Session.retrieve(sessionId);
        System.out.println("Stripe Session retrieved: " + session.getId());
        return "paid".equals(session.getPaymentStatus());
    }

}


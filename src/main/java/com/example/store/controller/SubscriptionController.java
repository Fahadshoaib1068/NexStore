package com.example.store.controller;

import com.example.store.model.CustomerSubscription;
import com.example.store.model.SubcriptionPlan;
import com.example.store.repository.SubscriptionRepository;
import com.example.store.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService    subscriptionService;

    public SubscriptionController(SubscriptionRepository subscriptionRepository,
                                  SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService    = subscriptionService;
    }

    // ─── GET ALL PLANS ───────────────────────────────────
    @GetMapping("/plans")
    public ResponseEntity<List<SubcriptionPlan>> getPlans() {
        return ResponseEntity.ok(subscriptionRepository.findAllPlans());
    }

    // ─── GET MY SUBSCRIPTION ──────────────────────────────────────
    @GetMapping("/my")
    public ResponseEntity<?> getMySubscription() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        CustomerSubscription sub = subscriptionRepository.findActiveByUsername(username);
        if (sub == null) {
            return ResponseEntity.ok(Map.of("message", "No active subscription"));
        }
        return ResponseEntity.ok(sub);
    }

    // ─── SUBSCRIBE ────────────────────────────────────────────────
    @PostMapping("/subscribe/{planId}")
    public ResponseEntity<?> subscribe(@PathVariable Integer planId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        CustomerSubscription existing = subscriptionRepository.findActiveByUsername(username);
        if (existing != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "You already have an active " + existing.getPlan_name() + " subscription"
            ));
        }

        SubcriptionPlan plan = subscriptionRepository.findPlanById(planId);
        if (plan == null) return ResponseEntity.notFound().build();

        try {
            // Step 1 — Save to DB first with placeholder to get subId
            Integer subId = subscriptionRepository.save(username, planId, "pending", "pending");

            // Step 2 — Create Stripe session with subId in success URL
            Map<String, String> stripeData = subscriptionService.createSubscriptionSession(
                    planId,
                    plan.getStripe_priceid(),
                    username,
                    username + "@store.com",
                    subId 
            );

            // Step 3 — Update DB with real Stripe session ID and customer ID
            subscriptionRepository.updateStripeSubId(subId, stripeData.get("sessionId"));
            subscriptionRepository.updateStripeCustomerId(subId, stripeData.get("stripeCustomerId"));

            System.out.println(" Subscription #" + subId + " created for " + username);

            return ResponseEntity.ok(Map.of(
                    "checkoutUrl", stripeData.get("checkoutUrl"),
                    "subId",       subId,
                    "plan",        plan.getPlan_name(),
                    "status",      "PENDING"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to create subscription: " + e.getMessage()
            ));
        }
    }

    // VERIFY SUBSCRIPTION called after Stripe redirect ───────
    @PostMapping("/verify/{subId}")
    public ResponseEntity<?> verifySubscription(@PathVariable Integer subId) {
        try {
            // Get subscription from DB
            CustomerSubscription sub = subscriptionRepository.findAll()
                    .stream()
                    .filter(s -> s.getSub_id().equals(subId))
                    .findFirst()
                    .orElse(null);

            if (sub == null) return ResponseEntity.notFound().build();

            // Verify with Stripe using session ID stored as stripe_sub_id
            Map<String, String> stripeData = subscriptionService.verifySubscription(
                    sub.getStripe_sub_id()
            );

            String paymentStatus = stripeData.get("paymentStatus");
            String stripeSubId   = stripeData.get("subscriptionId");

            if ("paid".equals(paymentStatus) || "no_payment_required".equals(paymentStatus)) {
                // Update DB with real Stripe subscription ID and mark ACTIVE
                subscriptionRepository.updateStatusByStripeSubId(sub.getStripe_sub_id(), "ACTIVE");

                // Store real stripe subscription ID for cancellation later
                subscriptionRepository.updateStripeSubId(subId, stripeSubId);

                System.out.println(" Subscription #" + subId + " ACTIVE for " + sub.getUsername());
                return ResponseEntity.ok(Map.of(
                        "status",  "ACTIVE",
                        "plan",    sub.getPlan_name(),
                        "subId",   subId
                ));
            } else {
                subscriptionRepository.updateStatus(subId, "FAILED");
                return ResponseEntity.ok(Map.of("status", "FAILED"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Verification failed: " + e.getMessage()
            ));
        }
    }

    // ─── CANCEL SUBSCRIPTION ─────────────────────────────────────
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelSubscription() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        CustomerSubscription sub = subscriptionRepository.findActiveByUsername(username);
        if (sub == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No active subscription found"
            ));
        }

        try {
            // Cancel on Stripe
            if (sub.getStripe_sub_id() != null) {
                subscriptionService.cancelSubscription(sub.getStripe_sub_id());
            }

            // Cancel in DB
            subscriptionRepository.cancel(sub.getSub_id());

            return ResponseEntity.ok(Map.of(
                    "message", "Subscription cancelled successfully",
                    "plan",    sub.getPlan_name()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to cancel subscription: " + e.getMessage()
            ));
        }
    }

    // ─── GET ALL SUBSCRIPTIONS (ADMIN only) ───────────────────────
    @GetMapping
    public ResponseEntity<List<CustomerSubscription>> getAllSubscriptions() {
        return ResponseEntity.ok(subscriptionRepository.findAll());
    }
}
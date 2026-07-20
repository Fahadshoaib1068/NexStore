package com.example.store.controller;

import com.example.store.model.Order;
import com.example.store.model.OrderDetail;
import com.example.store.repository.OrderRepository;
import com.example.store.service.StripeService;
import com.example.store.view.OrderView;
import com.example.store.view.OrderViewFactory;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.example.store.repository.SubscriptionRepository;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository  orderRepository;
    private final OrderViewFactory orderViewFactory;
    private final StripeService stripeService;
    private final SubscriptionRepository subscriptionRepository;

    public OrderController(OrderRepository orderRepository,
                           OrderViewFactory orderViewFactory,
                           StripeService stripeService,
                           SubscriptionRepository subscriptionRepository) {
        this.orderRepository        = orderRepository;
        this.orderViewFactory       = orderViewFactory;
        this.stripeService          = stripeService;
        this.subscriptionRepository = subscriptionRepository;
    }

    // ─── GET ORDERS (polymorphic — each role sees different data) ──
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        String role     = auth.getAuthorities().stream()
                .findFirst().get().getAuthority().replace("ROLE_", "");

        OrderView view = orderViewFactory.getView(role);
        return ResponseEntity.ok(view.getOrders(username));
    }

    // ─── GET ORDER BY ID ──────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetail> getOrderById(@PathVariable Integer id) {
        OrderDetail order = orderRepository.findById(id);
        return order != null
                ? ResponseEntity.ok(order)
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> payOrder(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        // finding the order
        Order basicOrder = orderRepository.findAll().stream()
                .filter(o-> o.getOrder_id().equals(id)).findFirst().orElse(null);

        if (basicOrder == null) {
            return ResponseEntity.notFound().build();
        }
        //only the customer can pay his order
        if(!username.equals(basicOrder.getCustomer_username())) {
            return ResponseEntity.status(403).body("You can pay only your own bill");
        }

        if("PAID".equals(basicOrder.getPayment_status())){
            return ResponseEntity.badRequest().body(Map.of("message", "Payment is already PAID"));
        }

        try{
// Get both session ID and checkout URL
            Map<String, String> stripeData = stripeService.createCheckoutSession(
                    id, basicOrder.getTotal_amount(), username
            );
            String sessionId   = stripeData.get("sessionId");
            String checkoutUrl = stripeData.get("checkoutUrl");

// Store just the session ID (short string like cs_test_xxx)
            orderRepository.updateStripeSession(id, sessionId);
            orderRepository.updatePaymentStatus(id, "PROCESSING");

            return ResponseEntity.ok(Map.of(
                    "checkoutUrl", checkoutUrl,
                    "orderId",     id,
                    "status",      "PROCESSING"
            ));

        } catch(Exception e){

            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/cancel-payment")
    public ResponseEntity<?> cancelPayment(@PathVariable Integer id) {
        orderRepository.updatePaymentStatus(id, "UNPAID");
        orderRepository.updateStripeSession(id, null);
        return ResponseEntity.ok(Map.of("message", "Payment cancelled, status reset to UNPAID"));
    }

    // verifying payment
    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyOrder(@PathVariable Integer id) {
        Order basicOrder = orderRepository.findAll().stream()
                .filter(o-> o.getOrder_id().equals(id)).findFirst().orElse(null);

        if (basicOrder == null) {
            return ResponseEntity.notFound().build();
        }
        if(basicOrder.getStripe_session_id() == null){
            return ResponseEntity.badRequest().body(Map.of("message", "No payment session found"));
        }
        try{
            boolean paid = stripeService.verifyPayment(basicOrder.getStripe_session_id());
            if(paid){
                orderRepository.updatePaymentStatus(id, "PAID");
                return ResponseEntity.ok(Map.of("status", "PAID", "orderid", id));
            } else{
                orderRepository.updatePaymentStatus(id, "UNPAID");
                return  ResponseEntity.ok(Map.of("status", "UNPAID", "orderid", id));
            }

        } catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── PLACE ORDER ──────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst().get().getAuthority().replace("ROLE_", "");

        if ("STAFF".equals(role)) {
            return ResponseEntity.status(403).body("Staff cannot place orders.");
        }

        Integer customer_id = (Integer) body.get("customer_id");
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

        Order order = new Order();
        order.setCustomer_id(customer_id);
        order.setCustomer_username(username);

        Integer order_id = orderRepository.save(order);
        if (order_id == null) return ResponseEntity.internalServerError().body("Failed to create order.");

        try {
            for (Map<String, Object> item : items) {
                Integer item_id  = (Integer) item.get("item_id");
                Integer quantity = (Integer) item.get("quantity");
                orderRepository.addOrderItem(order_id, item_id, quantity);
            }

            // ─── Apply subscription discount if customer has one ───
            Integer discount = subscriptionRepository.getDiscountForUser(username);
            if (discount > 0) {
                orderRepository.applyDiscount(order_id, discount);
                System.out.println("Applied " + discount + "% discount for " + username);
            }

        } catch (RuntimeException e) {
            orderRepository.delete(order_id);
            return ResponseEntity.status(400).body("Order failed: " + e.getMessage());
        }

        return ResponseEntity.ok("Order #" + order_id + " created successfully.");
    }

    // ─── DELETE ORDER ─────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteOrder(@PathVariable Integer id) {
        OrderDetail existing = orderRepository.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        orderRepository.delete(id);
        return ResponseEntity.ok("Order deleted successfully.");
    }
}
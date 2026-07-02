const BASE_URL = "/items";
const ORDERS_URL = "/orders";

// ── AUTH CHECK ───────────────────────────────────────────────────
const token = localStorage.getItem('token');
const userRole = localStorage.getItem('role');
const username = localStorage.getItem('username');

// Immediately show overlay if no token to prevent flicker
if (!token) {
    const overlay = document.getElementById('auth-overlay');
    if (overlay) overlay.style.display = 'flex';
    window.location.href = 'login.html';
}

function logout() {
    localStorage.clear();
    window.location.href = 'login.html';
}

function applyRolePermissions() {
    document.getElementById('user-display-name').textContent = username || 'User';
    document.getElementById('user-display-role').textContent = userRole || 'Customer';

    // Staff roles: STAFF, ADMIN, SUPER_ADMIN
    const isStaff = ['STAFF', 'ADMIN', 'SUPER_ADMIN'].includes(userRole);
    // Admin roles: ADMIN, SUPER_ADMIN
    const isAdmin = ['ADMIN', 'SUPER_ADMIN'].includes(userRole);

    if (isStaff) {
        document.querySelectorAll('.auth-staff').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
    }
    
    if (isAdmin) {
        document.querySelectorAll('.auth-admin').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
    }
}

// Helper for authorized fetch
async function authFetch(url, options = {}) {
    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch(url, options);
    if (res.status === 401 || res.status === 403) {
        // Token expired or insufficient permissions
        console.warn('Auth error', res.status);
        if (res.status === 401) {
            logout();
        }
    }
    return res;
}

// ── TAB SWITCHING ─────────────────────────────────────────────────
function switchTab(btn, name) {
    document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
    document.querySelectorAll(".panel").forEach(p => p.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById("panel-" + name).classList.add("active");
}

// ── MESSAGES ──────────────────────────────────────────────────────
function showMessage(id, text, isSuccess) {
    const el = document.getElementById(id);
    el.textContent = text;
    el.className = "message " + (isSuccess ? "success" : "error");
}

// ── STOCK BADGE ───────────────────────────────────────────────────
function stockBadge(qty) {
    if (qty === 0)  return `<span class="stock-badge stock-out">Out of stock</span>`;
    if (qty <= 5)   return `<span class="stock-badge stock-low">${qty} low</span>`;
    return `<span class="stock-badge stock-ok">${qty}</span>`;
}

// ── LOAD ALL ITEMS ────────────────────────────────────────────────
async function loadAllItems() {
    const tbody = document.getElementById("items-tbody");
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Loading inventory…</td></tr>`;

    try {
        const res = await authFetch(BASE_URL);
        if (!res.ok) throw new Error("Server error " + res.status);
        const items = await res.json();

        document.getElementById("stat-items").textContent = items.length + " items";

        if (items.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No products found. Add one above.</td></tr>`;
            return;
        }

        tbody.innerHTML = items.map((item, i) => `
            <tr style="animation-delay:${i * 40}ms">
                <td><span style="color:var(--text-muted);font-size:0.8rem">#${item.item_id}</span></td>
                <td><strong>${item.item_name}</strong></td>
                <td style="color:var(--text-muted)">${item.item_description ?? "—"}</td>
                <td><strong style="color:var(--blue)">$${Number(item.price).toFixed(2)}</strong></td>
                <td>${stockBadge(item.stock_quantity)}</td>
                <td>
                    <button class="row-delete-btn" onclick="deleteItemFromRow(${item.item_id}, '${item.item_name}')">
                        ✕ Delete
                    </button>
                </td>
            </tr>
        `).join("");

        showMessage("msg-view", `Loaded ${items.length} product${items.length !== 1 ? "s" : ""}.`, true);

    } catch (err) {
        showMessage("msg-view", "Could not load inventory: " + err.message, false);
        tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Failed to fetch data.</td></tr>`;
    }
}

// ── DELETE FROM ROW ───────────────────────────────────────────────
async function deleteItemFromRow(id, name) {
    if (!confirm(`Delete "${name}" (ID: ${id})? This cannot be undone.`)) return;

    try {
        const res = await authFetch(`${BASE_URL}/${id}`, { method: "DELETE" });
        const text = await res.text();
        if (res.ok) {
            showMessage("msg-view", text, true);
            loadAllItems();
        } else {
            showMessage("msg-view", text, false);
        }
    } catch (err) {
        showMessage("msg-view", "Request failed: " + err.message, false);
    }
}

// ── ADD ITEM ──────────────────────────────────────────────────────
async function addItem() {
    const body = {
        item_id:          parseInt(document.getElementById("add-id").value),
        item_name:        document.getElementById("add-name").value.trim(),
        item_description: document.getElementById("add-desc").value.trim(),
        price:            parseFloat(document.getElementById("add-price").value),
        stock_quantity:   parseInt(document.getElementById("add-stock").value)
    };

    if (!body.item_id || !body.item_name || isNaN(body.price) || isNaN(body.stock_quantity)) {
        showMessage("msg-add", "Please fill in all fields correctly.", false);
        return;
    }

    try {
        const res = await authFetch(BASE_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const text = await res.text();
        if (res.ok) {
            showMessage("msg-add", text, true);
            document.querySelectorAll("#panel-add input").forEach(i => i.value = "");
            loadAllItems();
        } else {
            showMessage("msg-add", text, false);
        }
    } catch (err) {
        showMessage("msg-add", "Request failed: " + err.message, false);
    }
}

// ── UPDATE ITEM ───────────────────────────────────────────────────
async function updateItem() {
    const id = parseInt(document.getElementById("upd-id").value);
    if (!id) { showMessage("msg-update", "Please enter a valid Item ID.", false); return; }

    const body = {
        item_name:        document.getElementById("upd-name").value.trim(),
        item_description: document.getElementById("upd-desc").value.trim(),
        price:            parseFloat(document.getElementById("upd-price").value),
        stock_quantity:   parseInt(document.getElementById("upd-stock").value)
    };

    if (!body.item_name || isNaN(body.price) || isNaN(body.stock_quantity)) {
        showMessage("msg-update", "Please fill in all fields correctly.", false);
        return;
    }

    try {
        const res = await authFetch(`${BASE_URL}/${id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const text = await res.text();
        showMessage("msg-update", text, res.ok);
        if (res.ok) {
            document.querySelectorAll("#panel-update input").forEach(i => i.value = "");
            loadAllItems();
        }
    } catch (err) {
        showMessage("msg-update", "Request failed: " + err.message, false);
    }
}

// ── LOAD ALL ORDERS ───────────────────────────────────────────────
async function loadAllOrders() {
    const tbody = document.getElementById("orders-tbody");
    tbody.innerHTML = `<tr><td colspan="5" class="empty-state">Loading orders…</td></tr>`;

    try {
        const res = await authFetch(ORDERS_URL);
        const orders = await res.json();

        document.getElementById("stat-orders").textContent = orders.length + " orders";

        if (orders.length === 0) {
            tbody.innerHTML = `<tr><td colspan="5" class="empty-state">No orders found.</td></tr>`;
            return;
        }

        tbody.innerHTML = orders.map((o, i) => `
            <tr style="animation-delay:${i * 40}ms">
                <td><span style="color:var(--text-muted);font-size:0.8rem">#${o.order_id}</span></td>
                <td>${o.customer_id}</td>
                <td style="color:var(--text-muted);font-size:0.82rem">${o.order_date ?? "—"}</td>
                <td><strong style="color:var(--emerald)">$${Number(o.total_amount).toFixed(2)}</strong></td>
                <td>
                    <button class="row-view-btn" onclick="loadOrderDetailById(${o.order_id})">⌕ View</button>
                    <button class="row-delete-btn" onclick="deleteOrderFromRow(${o.order_id})">✕</button>
                </td>
            </tr>
        `).join("");

        showMessage("msg-orders", `Loaded ${orders.length} order${orders.length !== 1 ? "s" : ""}.`, true);

    } catch (err) {
        showMessage("msg-orders", "Could not load orders: " + err.message, false);
    }
}

// ── LOAD ORDER DETAIL ─────────────────────────────────────────────
async function loadOrderDetail() {
    const id = document.getElementById("order-detail-id").value;
    if (!id) { alert("Please enter an Order ID"); return; }
    await loadOrderDetailById(id);
}

async function loadOrderDetailById(id) {
    const box = document.getElementById("order-detail-box");
    box.innerHTML = `<p style="color:var(--text-muted);font-size:0.85rem">Loading order #${id}…</p>`;

    try {
        const res = await authFetch(`${ORDERS_URL}/${id}`);
        if (!res.ok) { box.innerHTML = `<p style="color:var(--rose)">Order #${id} not found.</p>`; return; }
        const o = await res.json();

        box.innerHTML = `
            <div class="order-detail-card">
                <h3>Order #${o.order_id}</h3>
                <div class="detail-row"><span class="detail-label">Date</span><span class="detail-value">${o.order_date ?? "—"}</span></div>
                <div class="detail-row"><span class="detail-label">Total</span><span class="detail-value" style="color:var(--emerald)">$${Number(o.total_amount).toFixed(2)}</span></div>
                <div class="detail-row"><span class="detail-label">Customer</span><span class="detail-value">${o.customer_first_name} ${o.customer_last_name}</span></div>
                <div class="detail-row"><span class="detail-label">Email</span><span class="detail-value" style="color:var(--blue)">${o.customer_email}</span></div>
                <hr class="detail-divider"/>
                <p style="font-size:0.78rem;text-transform:uppercase;letter-spacing:0.05em;color:var(--text-muted);margin-bottom:0.5rem">Items in this order</p>
                <table class="order-items-table">
                    <thead>
                        <tr>
                            <th>Product</th>
                            <th>Qty</th>
                            <th>Unit Price</th>
                            <th>Subtotal</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${o.items.map(item => `
                            <tr>
                                <td>${item.item_name}</td>
                                <td>${item.quantity}</td>
                                <td>$${Number(item.unit_price).toFixed(2)}</td>
                                <td style="color:var(--emerald)">$${(item.quantity * item.unit_price).toFixed(2)}</td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    } catch (err) {
        box.innerHTML = `<p style="color:var(--rose)">Error: ${err.message}</p>`;
    }
}

// ── PLACE ORDER ───────────────────────────────────────────────────
async function placeOrder() {
    const customer_id = parseInt(document.getElementById("new-order-customer").value);
    const item_id     = parseInt(document.getElementById("new-order-item").value);
    const quantity    = parseInt(document.getElementById("new-order-qty").value);

    if (!customer_id || !item_id || !quantity) {
        showMessage("msg-place-order", "Please fill in all fields.", false);
        return;
    }

    try {
        const res = await authFetch(ORDERS_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ customer_id, items: [{ item_id, quantity }] })
        });
        const text = await res.text();
        showMessage("msg-place-order", text, res.ok);
        if (res.ok) {
            document.querySelectorAll("#panel-orders input").forEach(i => i.value = "");
        }
    } catch (err) {
        showMessage("msg-place-order", "Request failed: " + err.message, false);
    }
}

// ── DELETE ORDER ──────────────────────────────────────────────────
async function deleteOrder() {
    const id = parseInt(document.getElementById("delete-order-id").value);
    if (!id) { showMessage("msg-delete-order", "Please enter a valid Order ID.", false); return; }
    await deleteOrderFromRow(id);
}

async function deleteOrderFromRow(id) {
    if (!confirm(`Delete order #${id}? This cannot be undone.`)) return;
    try {
        const res = await authFetch(`${ORDERS_URL}/${id}`, { method: "DELETE" });
        const text = await res.text();
        if (res.ok) {
            showMessage("msg-orders", text, true);
            loadAllOrders();
        } else {
            showMessage("msg-delete-order", text, false);
        }
    } catch (err) {
        showMessage("msg-delete-order", "Request failed: " + err.message, false);
    }
}

// ── USER MANAGEMENT (PROMOTE) ─────────────────────────────────────
async function promoteUser() {
    const username = document.getElementById("promote-username").value.trim();
    const role = document.getElementById("promote-role").value;

    if (!username) {
        showMessage("msg-users", "Please enter a username.", false);
        return;
    }

    try {
        const res = await authFetch("/users/promote", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, role })
        });
        const data = await res.json();
        if (res.ok) {
            showMessage("msg-users", data.message, true);
            document.getElementById("promote-username").value = "";
        } else {
            showMessage("msg-users", data.error || "Failed to promote user", false);
        }
    } catch (err) {
        showMessage("msg-users", "Request failed: " + err.message, false);
    }
}

// ── AUTO LOAD on page ready ───────────────────────────────────────
window.addEventListener("DOMContentLoaded", () => {
    if (!token) return; // Already redirecting
    applyRolePermissions();
    loadAllItems();
});
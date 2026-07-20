const BASE_URL   = "/items";
const ORDERS_URL = "/orders";

// ── PAGINATION STATE ──────────────────────────────────────────────
let currentPage    = 0;
let currentFilters = {};
// currentItems = []; serverside wala ha
// client-side data
let allItems      = [];  // takes from the orignal db
let filteredItems = [];  // used for the searching 

const PAGE_SIZE    = 5;
let currentSortBy    = "item_id";
let currentDirection = "asc";

// ── ORDERS PAGINATION ─────────────────────────────────────────────
let currentPageOrders = 0;
let allOrders = [];
let filteredOrders = [];

// ── VIDEOS PAGINATION ─────────────────────────────────────────────
let currentPageVideos = 0;
let allVideos = [];
let filteredVideos = [];

// ── SUBSCRIPTIONS PAGINATION ──────────────────────────────────────
let currentPageSubs = 0;
let allSubscriptions = [];
let filteredSubscriptions = [];

// ── AUTH CHECK ────────────────────────────────────────────────────
const token    = localStorage.getItem('token');
const userRole = localStorage.getItem('role');
const username = localStorage.getItem('username');


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
    document.getElementById('user-display-role').textContent = userRole  || 'Customer';

    const isStaff      = ['STAFF', 'ADMIN', 'SUPER_ADMIN'].includes(userRole);
    const isAdmin      = ['ADMIN', 'SUPER_ADMIN'].includes(userRole);
    const isSuperAdmin = userRole === 'SUPER_ADMIN';
    const isCustomer   = userRole === 'CUSTOMER';

    if (isStaff)      document.querySelectorAll('.auth-staff').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
    if (isAdmin)      document.querySelectorAll('.auth-admin').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
    if (isSuperAdmin) document.querySelectorAll('.auth-superadmin').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
    if (isCustomer)   document.querySelectorAll('.auth-customer').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
}

// ── AUTH FETCH ────────────────────────────────────────────────────
async function authFetch(url, options = {}) {
    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch(url, options);
    if (res.status === 401) logout();
    return res;
}

// ── TAB SWITCHING ─────────────────────────────────────────────────
function switchTab(btn, name) {
    document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
    document.querySelectorAll(".panel").forEach(p => p.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById("panel-" + name).classList.add("active");
    if (name === "videos") loadAllVideos();
    if (name === "API") initAnalyticsTab(); else stopTimelinePolling();
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
    if (qty <= 10)  return `<span class="stock-badge stock-low">${qty} low</span>`;
    return `<span class="stock-badge stock-ok">${qty}</span>`;
}

// ── RENDER ITEMS ────────────────────────────────────────────────
function renderItems(items) {
    const tbody = document.getElementById("items-tbody");

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No items found.</td></tr>`;
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
}

// client site sorting
function sortTable(column, direction) {
    console.log("Sorting by", column, direction);
    switch (column) {
        case "name":
            currentSortBy = "item_name";
            break;
        case "price":
            currentSortBy = "price";
            break;
        case "stock":
            currentSortBy = "stock_quantity";
            break;
        default:
            currentSortBy = "item_id";
    }

    currentDirection = direction;
    currentPage = 0;

    filteredItems.sort((a, b) => {
        let valA = a[currentSortBy];
        let valB = b[currentSortBy];
        if (typeof valA === "string") valA = valA.toLowerCase();
        if (typeof valB === "string") valB = valB.toLowerCase();

        if (valA < valB) return currentDirection === "asc" ? -1 : 1;
        if (valA > valB) return currentDirection === "asc" ? 1 : -1;
        return 0;
    });

    renderCurrentPage();

    document.querySelectorAll(".sort-btn")
        .forEach(btn => btn.classList.remove("active"));

    if (event && event.target)
        event.target.classList.add("active");
}

// ── LOAD ALL ITEMS ─────────────────────────────────────────────────
async function loadAllItems() {
    console.log("Loading all items");
    currentPage    = 0;
    currentFilters = {};

    // Clear search inputs
    document.getElementById("search-name").value      = "";
    document.getElementById("search-min-price").value = "";
    document.getElementById("search-max-price").value = "";
    document.getElementById("search-min-stock").value = "";

    await loadItems();
}

// LOAD ITEMS FROM SERVER
async function loadItems() {
    const res = await authFetch(BASE_URL);
    allItems = await res.json();
    filteredItems = [...allItems];
    document.getElementById("stat-items").textContent = allItems.length + " items";
    renderCurrentPage();
}

// pagination
function renderCurrentPage() {
    const start = currentPage * PAGE_SIZE;
    const end   = start + PAGE_SIZE;
    const items = filteredItems.slice(start, end);
    renderItems(items);

    document.getElementById("page-info").textContent =
        `Page ${currentPage + 1} · ${items.length} item(s)`;

    document.getElementById("btn-prev").style.display =
        currentPage > 0 ? "inline-block" : "none";

    document.getElementById("btn-next").style.display =
        end < filteredItems.length ? "inline-block" : "none";
}

// Replacing fetchitem()
function applyFilter() {
    // adding search while typing on the search bar 
    const name     = document.getElementById("search-name").value.toLowerCase();
    const minPrice = parseFloat(document.getElementById("search-min-price").value);
    const maxPrice = parseFloat(document.getElementById("search-max-price").value);
    const minStock = parseInt(document.getElementById("search-min-stock").value);

    // adding filter only if 3 input
    const shouldFilterByName = name.length >= 3;
    const hasOtherFilters = !isNaN(minPrice) || !isNaN(maxPrice) || !isNaN(minStock);

    // show all items
    if (name.length < 0 && !hasOtherFilters) {
        filteredItems = [...allItems];
        currentPage = 0;
        renderCurrentPage();
        return;
    }

    filteredItems = allItems.filter(item => {
        if (shouldFilterByName && !item.item_name.toLowerCase().includes(name)) return false;
        if (!isNaN(minPrice) && item.price < minPrice) return false;
        if (!isNaN(maxPrice) && item.price > maxPrice) return false;
        if (!isNaN(minStock) && item.stock_quantity < minStock) return false;
        return true;
    });

    currentPage = 0;

    renderCurrentPage();
}

// Setup automatic search on input
function setupAutoSearch() {
    const searchNameInput = document.getElementById("search-name");
    if (searchNameInput) {
        searchNameInput.addEventListener("input", applyFilter);
    }

    const searchMinPrice = document.getElementById("search-min-price");
    const searchMaxPrice = document.getElementById("search-max-price");
    const searchMinStock = document.getElementById("search-min-stock");

    if (searchMinPrice) searchMinPrice.addEventListener("input", applyFilter);
    if (searchMaxPrice) searchMaxPrice.addEventListener("input", applyFilter);
    if (searchMinStock) searchMinStock.addEventListener("input", applyFilter);
}

// clear search
function clearSearch() {
    loadAllItems();
}

// pagination next page
function nextPage() {
    if ((currentPage + 1) * PAGE_SIZE >= filteredItems.length) return;
    currentPage++;
    renderCurrentPage();
}

// pagination prev page
function prevPage() {
    if (currentPage === 0) return;
    currentPage--;
    renderCurrentPage();
}

// ── DELETE FROM ROW
async function deleteItemFromRow(id, name) {
    if (!confirm(`Delete "${name}" (ID: ${id})? This cannot be undone.`)) return;

    try {
        const res = await authFetch(`${BASE_URL}/${id}`, { method: "DELETE" });
        const text = await res.text();
        if (res.ok) {
            showMessage("msg-view", text, true);
            setTimeout(() => loadAllItems(), 500);
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
            setTimeout(() => loadAllItems(), 500);
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
            setTimeout(() => loadAllItems(), 500);
        }
    } catch (err) {
        showMessage("msg-update", "Request failed: " + err.message, false);
    }
}

// ── LOAD ALL ORDERS ───────────────────────────────────────────────
async function loadAllOrders() {
    const tbody = document.getElementById("orders-tbody");
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Loading orders…</td></tr>`;

    try {
        const res    = await authFetch(ORDERS_URL);
        allOrders = await res.json();
        filteredOrders = allOrders;

        document.getElementById("stat-orders").textContent = allOrders.length + " orders";

        if (allOrders.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No orders found.</td></tr>`;
            return;
        }

        currentPageOrders = 0;
        renderOrdersPage();
        showMessage("msg-orders", `Loaded ${allOrders.length} order(s).`, true);

        // ─── Check if returning from Stripe ──────────────────────
        checkStripeReturn();

    } catch (err) {
        showMessage("msg-orders", "Could not load orders: " + err.message, false);
    }
}

// ── RENDER ORDERS PAGE ────────────────────────────────────────────
function renderOrdersPage() {
    const tbody = document.getElementById("orders-tbody");
    const start = currentPageOrders * PAGE_SIZE;
    const end   = start + PAGE_SIZE;
    const ordersToShow = filteredOrders.slice(start, end);

    const isCustomer   = userRole === 'CUSTOMER';
    const isStaff      = userRole === 'STAFF';
    const isAdmin      = ['ADMIN', 'SUPER_ADMIN'].includes(userRole);

    tbody.innerHTML = ordersToShow.map((o, i) => {
        const payBadge = () => {
            switch(o.payment_status) {
                case 'PAID':       return `<span class="status-badge status-completed"> Paid</span>`;
                case 'PROCESSING': return `<span class="status-badge status-processing"> Processing</span>`;
                case 'FAILED':     return `<span class="status-badge status-failed"> Failed</span>`;
                default:           return `<span class="status-badge status-pending"> Unpaid</span>`;
            }
        };

        const actions = () => {
            let btns = `<button class="row-view-btn" onclick="loadOrderDetailById(${o.order_id})">⌕ View</button>`;

            if (isCustomer && (o.payment_status === 'UNPAID' || o.payment_status == 'PROCESSING')) {
                btns += `<button class="action-btn success" 
                            style="padding:0.3rem 0.8rem;font-size:0.78rem;margin-left:0.4rem;"
                            onclick="initiatePayment(${o.order_id})">
                            💳 Pay
                         </button>`;
            }

            if (isAdmin) {
                btns += `<button class="row-delete-btn" 
                            style="margin-left:0.4rem;"
                            onclick="deleteOrderFromRow(${o.order_id})">✕</button>`;
            }

            return btns;
        };

        return `
            <tr style="animation-delay:${i * 40}ms">
                <td><span style="color:var(--text-muted);font-size:0.8rem">#${o.order_id}</span></td>
                <td>${o.customer_id}</td>
                <td style="color:var(--text-muted);font-size:0.82rem">${o.order_date ?? "—"}</td>
                <td><strong style="color:var(--emerald)">$${Number(o.total_amount).toFixed(2)}</strong></td>
                <td>${payBadge()}</td>
                <td>${actions()}</td>
            </tr>
        `;
    }).join("");

    document.getElementById("page-info-orders").textContent = 
        `Page ${currentPageOrders + 1} · ${filteredOrders.length} order(s)`;
    
    document.getElementById("btn-prev-orders").style.display = 
        currentPageOrders > 0 ? "inline-block" : "none";
    document.getElementById("btn-next-orders").style.display = 
        (currentPageOrders + 1) * PAGE_SIZE >= filteredOrders.length ? "none" : "inline-block";
}

// ── ORDERS PAGINATION ─────────────────────────────────────────────
function nextPageOrders() {
    if ((currentPageOrders + 1) * PAGE_SIZE >= filteredOrders.length) return;
    currentPageOrders++;
    renderOrdersPage();
}

function prevPageOrders() {
    if (currentPageOrders === 0) return;
    currentPageOrders--;
    renderOrdersPage();
}

// ── INITIATE STRIPE PAYMENT ───────────────────────────────────────
async function initiatePayment(orderId) {
    try {
        const res = await authFetch(`${ORDERS_URL}/${orderId}/pay`, { method: "POST" });

        const contentType = res.headers.get("content-type");

        if (contentType && contentType.includes("application/json")) {
            const data = await res.json();
            if (res.ok && data.checkoutUrl) {
                window.location.href = data.checkoutUrl;
            } else {
                showMessage("msg-orders", data.error || "Failed to initiate payment.", false);
            }
        } else {
            const text = await res.text();
            showMessage("msg-orders", text || "Failed to initiate payment.", false);
        }

    } catch (err) {
        showMessage("msg-orders", "Payment request failed: " + err.message, false);
    }
}


// ── CHECK STRIPE RETURN ───────────────────────────────────────────
async function checkStripeReturn() {
    const params  = new URLSearchParams(window.location.search);
    const payment = params.get("payment");
    const orderId = params.get("orderId");

    if (payment && orderId) {
        window.history.replaceState({}, document.title, "/index.html");

        if (payment === "success") {
            showMessage("msg-orders", " Verifying payment...", true);
            try {
                const res  = await authFetch(`${ORDERS_URL}/${orderId}/verify`, { method: "POST" });
                const data = await res.json();
                if (data.status === "PAID") {
                    showMessage("msg-orders", ` Order #${orderId} is now PAID.`, true);
                } else {
                    showMessage("msg-orders", ` Payment not confirmed for Order #${orderId}.`, false);
                }
                setTimeout(() => loadAllOrders(), 1500);
            } catch (err) {
                showMessage("msg-orders", "Verification failed: " + err.message, false);
            }
        } else if (payment === "cancelled") {
            try {
                await authFetch(`${ORDERS_URL}/${orderId}/cancel-payment`, { method: "POST" });
            } catch (err) {
                console.log("Could not reset payment status");
            }
            showMessage("msg-orders", `Payment cancelled for Order #${orderId}.`, false);
            setTimeout(() => loadAllOrders(), 1000);
        }
        return; // ← stop here for order payment
    }

    await checkSubscriptionReturn();
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
    box.scrollIntoView({ behavior: "smooth", block: "start" });

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
                    <thead><tr><th>Product</th><th>Qty</th><th>Unit Price</th><th>Subtotal</th></tr></thead>
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
        if (res.ok) document.querySelectorAll("#panel-orders input").forEach(i => i.value = "");
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
            setTimeout(() => loadAllOrders(), 500);
        } else {
            showMessage("msg-delete-order", text, false);
        }
    } catch (err) {
        showMessage("msg-delete-order", "Request failed: " + err.message, false);
    }
}

// ── USER MANAGEMENT ───────────────────────────────────────────────
async function promoteUser() {
    const username = document.getElementById("promote-username").value.trim();
    const role     = document.getElementById("promote-role").value;

    if (!username) { showMessage("msg-users", "Please enter a username.", false); return; }

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

// ── LOAD ALL SUBSCRIPTIONS (ADMIN VIEW) ───────────────────────────
async function loadAllSubscriptions() {
    const tbody = document.getElementById("subscriptions-tbody");
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Loading subscriptions…</td></tr>`;

    try {
        const res = await authFetch(SUBS_URL);
        allSubscriptions = await res.json();
        filteredSubscriptions = allSubscriptions;

        if (allSubscriptions.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No subscriptions found.</td></tr>`;
            return;
        }

        currentPageSubs = 0;
        renderSubscriptionsPage();
        showMessage("msg-subscriptions", `Loaded ${allSubscriptions.length} subscription(s).`, true);

    } catch (err) {
        showMessage("msg-subscriptions", "Could not load subscriptions: " + err.message, false);
    }
}

// ── RENDER SUBSCRIPTIONS PAGE ─────────────────────────────────────
function renderSubscriptionsPage() {
    const tbody = document.getElementById("subscriptions-tbody");
    const start = currentPageSubs * PAGE_SIZE;
    const end   = start + PAGE_SIZE;
    const subsToShow = filteredSubscriptions.slice(start, end);

    tbody.innerHTML = subsToShow.map((sub, i) => {
        const statusColor = sub.status === 'ACTIVE' ? 'status-completed' : 'status-pending';
        return `
            <tr style="animation-delay:${i * 40}ms">
                <td><span style="color:var(--text-muted);font-size:0.8rem">#${sub.sub_id}</span></td>
                <td><strong>${sub.username}</strong></td>
                <td>${sub.plan_name}</td>
                <td><span class="status-badge ${statusColor}"> ${sub.status}</span></td>
                <td style="color:var(--text-muted);font-size:0.82rem">${sub.started_at?.split('T')[0] ?? "—"}</td>
                <td style="color:var(--emerald);font-weight:600;">${sub.discount_pct}%</td>
            </tr>
        `;
    }).join("");

    document.getElementById("page-info-subs").textContent = 
        `Page ${currentPageSubs + 1} · ${filteredSubscriptions.length} subscription(s)`;
    
    document.getElementById("btn-prev-subs").style.display = 
        currentPageSubs > 0 ? "inline-block" : "none";
    document.getElementById("btn-next-subs").style.display = 
        (currentPageSubs + 1) * PAGE_SIZE >= filteredSubscriptions.length ? "none" : "inline-block";
}

// ── SUBSCRIPTIONS PAGINATION ──────────────────────────────────────
function nextPageSubs() {
    if ((currentPageSubs + 1) * PAGE_SIZE >= filteredSubscriptions.length) return;
    currentPageSubs++;
    renderSubscriptionsPage();
}

function prevPageSubs() {
    if (currentPageSubs === 0) return;
    currentPageSubs--;
    renderSubscriptionsPage();
}

// ── AUTO LOAD ─────────────────────────────────────────────────────
window.addEventListener("DOMContentLoaded", () => {
    if (!token) return;
    applyRolePermissions();
    loadAllItems();
    setupAutoSearch();
    checkSubscriptionReturn();
    if (userRole === "CUSTOMER") {
        loadSubscriptionPlans();
    }
});

// ── VIDEOS (CCTV) ──────────────────────────────────────────────────
const VIDEOS_URL = "/videos";
let videoQualitiesMap = {};

async function loadAllVideos() {
    const tbody = document.getElementById("videos-tbody");
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Loading recordings…</td></tr>`;

    try {
        const res = await authFetch(VIDEOS_URL);
        if (!res.ok) throw new Error("Server error " + res.status);
        allVideos = await res.json();
        filteredVideos = allVideos;

        if (allVideos.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No recordings uploaded yet.</td></tr>`;
        } else {
            currentPageVideos = 0;
            renderVideosPage();
        }

        await loadCompletedVideos();

    } catch (err) {
        showMessage("msg-videos", "Could not load recordings: " + err.message, false);
        tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Failed to fetch data.</td></tr>`;
    }
}

// ── RENDER VIDEOS PAGE ────────────────────────────────────────────
function renderVideosPage() {
    const tbody = document.getElementById("videos-tbody");
    const start = currentPageVideos * PAGE_SIZE;
    const end   = start + PAGE_SIZE;
    const videosToShow = filteredVideos.slice(start, end);

    tbody.innerHTML = videosToShow.map((v, i) => `
        <tr style="animation-delay:${i * 40}ms">
            <td><span style="color:var(--text-muted);font-size:0.8rem">#${v.video_id}</span></td>
            <td><strong>${escapeHtml(v.original_name)}</strong></td>
            <td style="color:var(--text-muted)">${v.uploaded_by ?? "—"}</td>
            <td style="color:var(--text-muted);font-size:0.82rem">${v.uploaded_at ?? "—"}</td>
            <td>${videoStatusBadge(v.status)}</td>
            <td>
                ${v.status === "PENDING"
            ? `<button class="row-view-btn" onclick="processVideo(${v.video_id})">▶ Process</button>`
            : `<span style="color:var(--text-muted);font-size:0.78rem">—</span>`}
            </td>
        </tr>
    `).join("");

    document.getElementById("page-info-videos").textContent = 
        `Page ${currentPageVideos + 1} · ${filteredVideos.length} recording(s)`;
    
    document.getElementById("btn-prev-videos").style.display = 
        currentPageVideos > 0 ? "inline-block" : "none";
    document.getElementById("btn-next-videos").style.display = 
        (currentPageVideos + 1) * PAGE_SIZE >= filteredVideos.length ? "none" : "inline-block";
}

// ── VIDEOS PAGINATION ─────────────────────────────────────────────
function nextPageVideos() {
    if ((currentPageVideos + 1) * PAGE_SIZE >= filteredVideos.length) return;
    currentPageVideos++;
    renderVideosPage();
}

function prevPageVideos() {
    if (currentPageVideos === 0) return;
    currentPageVideos--;
    renderVideosPage();
}

function videoStatusBadge(status) {
    const map = {
        PENDING: "status-pending", PROCESSING: "status-processing",
        COMPLETED: "status-completed", FAILED: "status-failed"
    };
    return `<span class="status-badge ${map[status] || "status-pending"}">${status}</span>`;
}

function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str ?? "";
    return div.innerHTML;
}

async function uploadVideo() {
    const input = document.getElementById("video-file-input");
    const file  = input.files[0];

    if (!file) {
        showMessage("msg-video-upload", "Please choose a .mp4 or .mov file first.", false);
        return;
    }
    const ext = file.name.toLowerCase().slice(file.name.lastIndexOf('.'));
    if (ext !== ".mp4" && ext !== ".mov") {
        showMessage("msg-video-upload", "Only .mp4 and .mov files are allowed.", false);
        return;
    }

    const formData = new FormData();
    formData.append("file", file);
    showMessage("msg-video-upload", "Uploading…", true);

    try {
        const res  = await authFetch(`${VIDEOS_URL}/upload`, { method: "POST", body: formData });
        const data = await res.json();
        if (res.ok) {
            showMessage("msg-video-upload", `Uploaded — video #${data.video_id} is now PENDING.`, true);
            input.value = "";
            setTimeout(() => loadAllVideos(), 500);
        } else {
            showMessage("msg-video-upload", data.error || "Upload failed.", false);
        }
    } catch (err) {
        showMessage("msg-video-upload", "Request failed: " + err.message, false);
    }
}

async function processVideo(id) {
    if (!confirm(`Start processing video #${id}? This will generate 360p / 480p / 720p versions.`)) return;
    try {
        const res  = await authFetch(`${VIDEOS_URL}/${id}/process`, { method: "POST" });
        const data = await res.json();
        showMessage("msg-videos", res.ok ? `Video #${id} queued for processing.` : (data.error || "Could not start processing."), res.ok);
        setTimeout(() => loadAllVideos(), 500);
    } catch (err) {
        showMessage("msg-videos", "Request failed: " + err.message, false);
    }
}

async function loadCompletedVideos() {
    const grid = document.getElementById("completed-videos-grid");
    try {
        const res = await authFetch(`${VIDEOS_URL}/completed`);
        if (!res.ok) throw new Error("Server error " + res.status);
        const videos = await res.json();

        if (videos.length === 0) {
            grid.innerHTML = `<p class="empty-state">No completed recordings yet.</p>`;
            return;
        }

        const cards = await Promise.all(videos.map(async (v) => {
            const pRes      = await authFetch(`${VIDEOS_URL}/${v.video_id}/processed`);
            const qualities = pRes.ok ? await pRes.json() : [];
            videoQualitiesMap[v.video_id] = { name: v.original_name, qualities };

            // Thumbnail URL — fallback to placeholder if no thumbnail
            const thumbSrc = v.thumbnail_path
                ? `${VIDEOS_URL}/thumbnail/${v.thumbnail_path}`
                : `https://placehold.co/320x180/1a1a2e/4f8ef7?text=No+Thumbnail`;

            return `
                <div class="video-card" onclick="openVideoModal(${v.video_id}, '${qualities[0]?.quality || ''}')">
                    <div class="video-thumb-wrapper">
                        <img src="${thumbSrc}" 
                             alt="${escapeHtml(v.original_name)}"
                             class="video-thumbnail"
                             onerror="this.src='https://placehold.co/320x180/1a1a2e/4f8ef7?text=No+Thumbnail'"/>
                        <div class="play-overlay">▶</div>
                    </div>
                    <div class="video-card-info">
                        <h4>${escapeHtml(v.original_name)}</h4>
                        <div class="video-meta">#${v.video_id} · ${v.uploaded_by ?? "—"}</div>
                        <div class="video-qualities">
                            ${qualities.map(q =>
                `<button class="quality-btn" 
                                    onclick="event.stopPropagation(); openVideoModal(${v.video_id}, '${q.quality}')">
                                    ${q.quality}
                                </button>`
            ).join("")}
                        </div>
                    </div>
                </div>
            `;
        }));

        grid.innerHTML = cards.join("");

    } catch (err) {
        grid.innerHTML = `<p class="empty-state">Failed to load processed recordings.</p>`;
    }
}

function openVideoModal(videoId, quality) {
    const entry = videoQualitiesMap[videoId];
    if (!entry) return;

    const modal    = document.getElementById("video-modal");
    const player   = document.getElementById("video-modal-player");
    const title    = document.getElementById("video-modal-title");
    const switcher = document.getElementById("quality-switch");

    title.textContent = entry.name;

    switcher.innerHTML = entry.qualities.map(q => `
        <button class="quality-btn ${q.quality === quality ? 'active' : ''}"
                onclick="openVideoModal(${videoId}, '${q.quality}')">${q.quality}</button>
    `).join("");

    const match = entry.qualities.find(q => q.quality === quality);
    if (match) {
        player.src = `${VIDEOS_URL}/stream/${match.file_path}`;
        player.load();
        player.play().catch(() => {});
    }

    modal.classList.add("open");
}

function closeVideoModal() {
    const modal  = document.getElementById("video-modal");
    const player = document.getElementById("video-modal-player");
    player.pause();
    player.removeAttribute("src");
    player.load();
    modal.classList.remove("open");
}
// ── LIVE API EVENT STREAM ────────────────────────────────────────

let totalRequest = 0;
let apiSet = new Set();
let totalDuration = 0;
let totalError = 0;

let timelineTimer = null;

async function fetchTimeline() {
    const res = await fetch(`/analytics/timeline`, {
        headers: { Authorization: `Bearer ${token}` }
    });
    const data = await res.json();
    renderLineChart(data.windowStart, data.buckets);
}

function startTimelinePolling() {
    if (timelineTimer) return;
    fetchTimeline(); // draw immediately, don't wait 30s for first point
    timelineTimer = setInterval(fetchTimeline, 30000);
}

function stopTimelinePolling() {
    if (timelineTimer) {
        clearInterval(timelineTimer);
        timelineTimer = null;
    }
}

function renderLineChart(windowStart, history) {
    const svg = d3.select("#chart");
    svg.selectAll("*").remove();

    const width  = svg.node().clientWidth || 600;
    const height = 260;
    const margin = { top: 20, right: 30, bottom: 30, left: 40 };
    svg.attr("height", height);

    if (!history || history.length === 0) {
        svg.append("text")
            .attr("x", 20).attr("y", 40)
            .attr("fill", "#94a3b8").attr("font-size", "13px")
            .text("Waiting for API activity...");
        return;
    }

    const windowStartMs = new Date(windowStart).getTime();
    const data = history.map(d => ({ ...d, time: new Date(windowStartMs + d.bucket * 30000) }));

    const x = d3.scaleTime()
        .domain(d3.extent(data, d => d.time))
        .range([margin.left, width - margin.right]);

    const maxY = d3.max(data, d => d.count) || 1;
    const y = d3.scaleLinear()
        .domain([0, maxY + 1])
        .range([height - margin.bottom, margin.top]);

    const line = d3.line()
        .x(d => x(d.time))
        .y(d => y(d.count))
        .curve(d3.curveMonotoneX);

    svg.append("g")
        .attr("transform", `translate(0,${height - margin.bottom})`)
        .call(d3.axisBottom(x).ticks(5).tickFormat(d3.timeFormat("%H:%M:%S")))
        .attr("color", "#94a3b8");

    svg.append("g")
        .attr("transform", `translate(${margin.left},0)`)
        .call(d3.axisLeft(y).ticks(Math.min(maxY + 1, 6)))
        .attr("color", "#94a3b8");

    svg.append("path")
        .datum(data)
        .attr("fill", "none")
        .attr("stroke", "#4f8ef7")
        .attr("stroke-width", 2)
        .attr("d", line);

    const tooltip = d3.select("#chart-tooltip");

    svg.selectAll("circle")
        .data(data)
        .enter()
        .append("circle")
        .attr("cx", d => x(d.time))
        .attr("cy", d => y(d.count))
        .attr("r", 5)
        .attr("fill", "#4f8ef7")
        .style("cursor", "pointer")
        .on("mouseover", (event, d) => {
            tooltip
                .style("display", "block")
                .html(`
                    <strong>Time:</strong> ${d.time.toLocaleTimeString()}<br/>
                    <strong>APIs Hit:</strong> ${d.count}<br/>
                    ${d.apis.map(a => `• ${a}`).join("<br/>")}
                `);
        })
        .on("mousemove", (event) => {
            tooltip
                .style("left", (event.pageX + 12) + "px")
                .style("top",  (event.pageY - 10) + "px");
        })
        .on("mouseout", () => tooltip.style("display", "none"));
}

async function loadInitialAnalytics() {
    const res = await fetch(`/analytics/hits`, {
        headers: { Authorization: `Bearer ${token}` }
    });
    const data = await res.json();

    totalRequest = data.totalRequests;
    apiSet = new Set(Object.keys(data.hits));
    totalDuration = data.avgResponseMs * data.totalRequests;
    totalError = Math.round(data.errorRate / 100 * data.totalRequests);

    document.getElementById("totalRequests").textContent = totalRequest;
    document.getElementById("totalApis").textContent = apiSet.size;
    document.getElementById("avgTime").textContent = data.avgResponseMs + "ms";
    document.getElementById("errorRate").textContent = data.errorRate + "%";
}

async function initAnalyticsTab() {
    await loadInitialAnalytics();
    startTimelinePolling();
}

const SUBS_URL = "/subscriptions";

// ── LOAD SUBSCRIPTION PLANS ───────────────────────────────────────
async function loadSubscriptionPlans() {
    const grid = document.getElementById("plans-grid");
    grid.innerHTML = `<p class="empty-state">Loading plans...</p>`;

    try {
        // Load plans and current subscription simultaneously
        const [plansRes, mySubRes] = await Promise.all([
            authFetch(`${SUBS_URL}/plans`),
            authFetch(`${SUBS_URL}/my`)
        ]);

        const plans  = await plansRes.json();
        const mySub  = mySubRes.ok ? await mySubRes.json() : null;

        // Show current subscription status
        renderCurrentSub(mySub);

        if (plans.length === 0) {
            grid.innerHTML = `<p class="empty-state">No plans available.</p>`;
            return;
        }

        grid.innerHTML = plans.map(plan => {
            const isActive = mySub && mySub.plan_id === plan.plan_id && mySub.status === 'ACTIVE';
            const hasOther = mySub && mySub.status === 'ACTIVE' && mySub.plan_id !== plan.plan_id;

            return `
                <div class="plan-card ${isActive ? 'plan-active' : ''}">
                    <div class="plan-header-bar">
                        <h3 class="plan-name">${plan.plan_name}</h3>
                        ${isActive ? `<span class="status-badge status-completed"> Active</span>` : ''}
                    </div>
                    <div class="plan-price">
                        <span class="plan-amount">$${Number(plan.price).toFixed(2)}</span>
                        <span class="plan-period">/month</span>
                    </div>
                    <p class="plan-desc">${plan.description}</p>
                    <div class="plan-discount">
                        <span style="color:var(--emerald);font-size:0.85rem;font-weight:600;">
                            ${plan.discount_pct > 0 ? `🏷 ${plan.discount_pct}% off all orders` : '⭐ Early access to new items'}
                        </span>
                    </div>
                    <button 
                        class="action-btn ${isActive ? 'danger' : 'success'}"
                        style="width:100%;margin-top:1rem;"
                        onclick="${isActive ? 'cancelSubscription()' : hasOther ? 'alert(\'Cancel your current plan first\')' : `subscribeToPlan(${plan.plan_id})`}"
                        ${hasOther ? 'disabled style="width:100%;margin-top:1rem;opacity:0.5;cursor:not-allowed;"' : ''}>
                        ${isActive ? '✕ Cancel Plan' : hasOther ? 'Unavailable' : '+ Subscribe'}
                    </button>
                </div>
            `;
        }).join("");

    } catch (err) {
        grid.innerHTML = `<p class="empty-state">Failed to load plans.</p>`;
    }
}

// ── RENDER CURRENT SUBSCRIPTION ───────────────────────────────────
function renderCurrentSub(sub) {
    const box = document.getElementById("current-sub-box");

    if (!sub || sub.message) {
        box.innerHTML = `
            <div style="background:var(--navy-card);border:1px solid var(--border);border-radius:var(--radius);padding:1rem;">
                <p style="color:var(--text-muted);font-size:0.9rem;">You don't have an active subscription. Subscribe below to get discounts!</p>
            </div>
        `;
        return;
    }

    box.innerHTML = `
        <div style="background:rgba(16,185,129,0.08);border:1px solid rgba(16,185,129,0.25);border-radius:var(--radius);padding:1.25rem;">
            <div style="display:flex;justify-content:space-between;align-items:center;">
                <div>
                    <h3 style="color:var(--emerald);font-family:'Syne',sans-serif;">${sub.plan_name} Plan</h3>
                    <p style="color:var(--text-muted);font-size:0.85rem;margin-top:0.3rem;">
                        ${sub.discount_pct}% discount on all orders · Started ${sub.started_at?.split('T')[0] ?? '—'}
                    </p>
                </div>
                <span class="status-badge status-completed"> Active</span>
            </div>
        </div>
    `;
}

// ── SUBSCRIBE TO PLAN ─────────────────────────────────────────────
async function subscribeToPlan(planId) {
    try {
        const res  = await authFetch(`${SUBS_URL}/subscribe/${planId}`, { method: "POST" });
        const data = await res.json();

        if (res.ok && data.checkoutUrl) {
            // Store subId so we can verify after redirect
            localStorage.setItem("pending_sub_id", data.subId);
            window.location.href = data.checkoutUrl;
        } else {
            showMessage("msg-subscription", data.error || "Failed to subscribe.", false);
        }
    } catch (err) {
        showMessage("msg-subscription", "Request failed: " + err.message, false);
    }
}

// ── CANCEL SUBSCRIPTION ───────────────────────────────────────────
async function cancelSubscription() {
    if (!confirm("Are you sure you want to cancel your subscription? You will lose your discount.")) return;

    try {
        const res  = await authFetch(`${SUBS_URL}/cancel`, { method: "POST" });
        const data = await res.json();

        if (res.ok) {
            showMessage("msg-subscription", ` ${data.message}`, true);
            setTimeout(() => loadSubscriptionPlans(), 1000);
        } else {
            showMessage("msg-subscription", data.error || "Failed to cancel.", false);
        }
    } catch (err) {
        showMessage("msg-subscription", "Request failed: " + err.message, false);
    }
}

// ── CHECK SUBSCRIPTION RETURN (add to checkStripeReturn) ──────────
async function checkSubscriptionReturn() {
    const params       = new URLSearchParams(window.location.search);
    const subscription = params.get("subscription");
    const subId        = params.get("subId");        // FROM URL not localStorage

    if (!subscription || !subId) return;

    window.history.replaceState({}, document.title, "/index.html");
    localStorage.removeItem("pending_sub_id");

    if (subscription === "success") {
        showMessage("msg-subscription", "Verifying subscription...", true);

        try {
            const res  = await authFetch(`${SUBS_URL}/verify/${subId}`, { method: "POST" });
            const data = await res.json();

            console.log("Verify response:", data);

            if (data.status === "ACTIVE") {
                showMessage("msg-subscription",
                    `Subscription activated! You now get ${data.plan} discounts.`, true);
                setTimeout(() => loadSubscriptionPlans(), 1500);
            } else {
                showMessage("msg-subscription", "Subscription not confirmed yet.", false);
            }
        } catch (err) {
            showMessage("msg-subscription", "Verification failed: " + err.message, false);
        }

    } else if (subscription === "cancelled") {
        showMessage("msg-subscription", "Subscription cancelled.", false);
    }
}



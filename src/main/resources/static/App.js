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
    const isAdmin       = ['ADMIN', 'SUPER_ADMIN'].includes(userRole);
    const isSuperAdmin  = userRole === 'SUPER_ADMIN';

    if (isStaff)      document.querySelectorAll('.auth-staff').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
    if (isAdmin)       document.querySelectorAll('.auth-admin').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
    if (isSuperAdmin)  document.querySelectorAll('.auth-superadmin').forEach(el => el.style.setProperty('display', 'inline-block', 'important'));
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
    if (name === "API") startAnalyticsPolling(); else stopAnalyticsPolling();
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

/*
async function searchItems() {
    currentPage = 0;
    currentFilters = {
        name:     document.getElementById("search-name").value.trim(),
        minPrice: document.getElementById("search-min-price").value,
        maxPrice: document.getElementById("search-max-price").value,
        minStock: document.getElementById("search-min-stock").value
    };

    const hasFilters = Object.values(currentFilters).some(v => v !== "");
    if (!hasFilters) { loadAllItems(); return; }

    await fetchItems();
}

async function changePage(direction) {
    currentPage += direction;
    if (currentPage < 0) currentPage = 0;
    await fetchItems();
}

async function fetchItems() {
    const tbody = document.getElementById("items-tbody");
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Loading...</td></tr>`;

    let url = `/items/search?page=${currentPage}&size=${PAGE_SIZE}&sortBy=${currentSortBy}&direction=${currentDirection}`;
    if (currentFilters.name)     url += `&name=${encodeURIComponent(currentFilters.name)}`;
    if (currentFilters.minPrice) url += `&minPrice=${currentFilters.minPrice}`;
    if (currentFilters.maxPrice) url += `&maxPrice=${currentFilters.maxPrice}`;
    if (currentFilters.minStock) url += `&minStock=${currentFilters.minStock}`;

    try {
        const res = await authFetch(url);
        if (!res.ok) throw new Error("Server error " + res.status);
        const items = await res.json();

        document.getElementById("stat-items").textContent = items.length + " items";

        if (items.length === 0 && currentPage === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No items found.</td></tr>`;
            document.getElementById("page-info").textContent  = "";
            document.getElementById("btn-prev").style.display = "none";
            document.getElementById("btn-next").style.display = "none";
            return;
        }

        renderItems(items);

        document.getElementById("page-info").textContent =
            `Page ${currentPage + 1} · ${items.length} item(s)`;
        document.getElementById("btn-prev").style.display =
            currentPage > 0 ? "inline-block" : "none";
        document.getElementById("btn-next").style.display =
            items.length === PAGE_SIZE ? "inline-block" : "none";

        showMessage("msg-view", `Loaded ${items.length} product(s).`, true);

    } catch (err) {
        showMessage("msg-view", "Could not load inventory: " + err.message, false);
        tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Failed to fetch data.</td></tr>`;
    }
}
 */

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

        showMessage("msg-orders", `Loaded ${orders.length} order(s).`, true);

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

// ── AUTO LOAD ─────────────────────────────────────────────────────
window.addEventListener("DOMContentLoaded", () => {
    if (!token) return;
    applyRolePermissions();
    loadAllItems();
    setupAutoSearch();
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
        const videos = await res.json();

        if (videos.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No recordings uploaded yet.</td></tr>`;
        } else {
            tbody.innerHTML = videos.map((v, i) => `
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
        }

        await loadCompletedVideos();

    } catch (err) {
        showMessage("msg-videos", "Could not load recordings: " + err.message, false);
        tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Failed to fetch data.</td></tr>`;
    }
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

let analyticsInterval = null;

function startAnalyticsPolling() {
    loadAnalytics();
    if (analyticsInterval) clearInterval(analyticsInterval);
    analyticsInterval = setInterval(loadAnalytics, 3000);
}

function stopAnalyticsPolling() {
    if (analyticsInterval) clearInterval(analyticsInterval);
    analyticsInterval = null;
}

async function loadAnalytics() {
    try {
        const res = await authFetch("/analytics/hits");
        if (!res.ok) return;
        const data = await res.json();

        document.getElementById("totalRequests").textContent = data.totalRequests;
        document.getElementById("totalApis").textContent      = data.totalApis;
        document.getElementById("avgTime").textContent        = data.avgResponseMs + " ms";
        document.getElementById("errorRate").textContent      = data.errorRate + "%";

        renderApiChart(data.hits);
    } catch (err) {
        console.error("Analytics fetch failed:", err);
    }
}

function renderApiChart(hits) {
    const entries = Object.entries(hits).sort((a, b) => b[1] - a[1]);
    const svg = d3.select("#chart");
    svg.selectAll("*").remove();

    if (entries.length === 0) {
        svg.attr("height", 60);
        svg.append("text")
            .attr("x", 10).attr("y", 30)
            .attr("fill", "#94a3b8").attr("font-size", "13px")
            .text("No API traffic recorded yet.");
        return;
    }

    const rowHeight = 48;
    const barHeight = 28;
    const labelWidth = 140;
    const width  = svg.node().clientWidth || 600;
    const height = entries.length * rowHeight + 10;
    svg.attr("height", height);

    const maxVal  = d3.max(entries, d => d[1]) || 1;
    const scaleX  = d3.scaleLinear().domain([0, maxVal]).range([0, width - labelWidth - 60]);

    const rows = svg.selectAll("g.row")
        .data(entries)
        .enter()
        .append("g")
        .attr("class", "row")
        .attr("transform", (d, i) => `translate(0, ${i * rowHeight})`);

    rows.append("text")
        .attr("x", 0).attr("y", barHeight / 2).attr("dy", "0.35em")
        .attr("fill", "#94a3b8").attr("font-size", "13px")
        .text(d => d[0]);

    rows.append("rect")
        .attr("x", labelWidth).attr("y", 0)
        .attr("height", barHeight).attr("rx", 4)
        .attr("fill", "#4f8ef7")
        .attr("width", 0)
        .transition().duration(300)
        .attr("width", d => scaleX(d[1]));

    rows.append("text")
        .attr("x", d => labelWidth + scaleX(d[1]) + 8)
        .attr("y", barHeight / 2).attr("dy", "0.35em")
        .attr("fill", "#e2e8f0").attr("font-size", "13px")
        .text(d => d[1]);
}
document.addEventListener("DOMContentLoaded", () => {

    const loginForm = document.getElementById("login-form");
    const registerForm = document.getElementById("register-form");
    const showRegister = document.getElementById("show-register");
    const showLogin = document.getElementById("show-login");
    const authTitle = document.getElementById("auth-title");
    const authSubtitle = document.getElementById("auth-subtitle");
    const messageEl = document.getElementById("auth-message");

    document.querySelectorAll(".toggle-password").forEach(toggle => {

        toggle.addEventListener("click", () => {

            const input = toggle.previousElementSibling;
            const icon = toggle.querySelector("i");

            if (input.type === "password") {
                input.type = "text";
                icon.classList.remove("fa-eye");
                icon.classList.add("fa-eye-slash");
            } else {
                input.type = "password";
                icon.classList.remove("fa-eye-slash");
                icon.classList.add("fa-eye");
            }
        });
    });

    showRegister.addEventListener("click", (e) => {
        e.preventDefault();
        loginForm.classList.add("hidden");
        registerForm.classList.remove("hidden");
        authTitle.textContent = "Create Account";
        authSubtitle.textContent = "Join NexStore today.";
        clearMessage();
    });

    showLogin.addEventListener("click", (e) => {
        e.preventDefault();
        registerForm.classList.add("hidden");
        loginForm.classList.remove("hidden");
        authTitle.textContent = "Welcome Back";
        authSubtitle.textContent = "Sign in to continue shopping.";
        clearMessage();
    });

    function showMessage(text, success) {
        messageEl.textContent = text;
        messageEl.className = "message " + (success ? "success" : "error");
    }
    function clearMessage() {
        messageEl.className = "message";
    }

    function setLoading(button, loading) {
        if (loading) {
            button.classList.add("loading");
            button.disabled = true;
        } else {
            button.classList.remove("loading");
            button.disabled = false;
        }
    }

    function shakeInput(input) {
        input.parentElement.style.animation = "shake .45s";
        setTimeout(() => {
            input.parentElement.style.animation = "";
        }, 450);
    }

    loginForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const username =
            document.getElementById("login-username");
        const password =
            document.getElementById("login-password");
        if (!username.value.trim()) {
            shakeInput(username);
            return;
        }
        if (!password.value.trim()) {
            shakeInput(password);
            return;
        }
        const button = loginForm.querySelector(".auth-btn");
        setLoading(button, true);
        try {
            const res = await fetch("/auth/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    username: username.value,
                    password: password.value
                })
            });
            const data = await res.json();
            if (res.ok) {
                localStorage.setItem("token", data.token);
                localStorage.setItem("username", data.username);
                localStorage.setItem("role", data.role);
                showMessage("Login successful! Redirecting...", true);
                setTimeout(() => {
                    window.location.href = "index.html";
                }, 1200);
            } else {
                showMessage(data.error || "Login failed.", false);
            }
        } catch (err) {
            showMessage("Unable to connect to server.", false);
        } finally {
            setLoading(button, false);
        }
    });

    registerForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const username =
            document.getElementById("reg-username");
        const email =
            document.getElementById("reg-email");
        const password =
            document.getElementById("reg-password");
        if (!username.value.trim()) {
            shakeInput(username);
            return;
        }
        if (!email.value.trim()) {
            shakeInput(email);
            return;
        }
        if (!password.value.trim()) {
            shakeInput(password);
            return;
        }
        const button = registerForm.querySelector(".auth-btn");
        setLoading(button, true);
        try {
            const res = await fetch("/auth/register", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({username: username.value, email: email.value, password: password.value})
            });
            const data = await res.json();
            if (res.ok) {
                showMessage("Registration successful! Please login.", true);
                registerForm.reset();
                setTimeout(() => {
                    showLogin.click();
                }, 1800);
            } else {
                showMessage(data.error || "Registration failed.", false);
            }
        } catch (err) {
            showMessage("Unable to connect to server.", false);
        } finally {
            setLoading(button, false);
        }
    });
});
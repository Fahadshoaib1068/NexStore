document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const showRegister = document.getElementById('show-register');
    const showLogin = document.getElementById('show-login');
    const authSubtitle = document.getElementById('auth-subtitle');
    const messageEl = document.getElementById('auth-message');

    // Switch to Register
    showRegister.addEventListener('click', (e) => {
        e.preventDefault();
        loginForm.classList.add('hidden');
        registerForm.classList.remove('hidden');
        authSubtitle.textContent = 'Create your account.';
        messageEl.style.display = 'none';
    });

    // Switch to Login
    showLogin.addEventListener('click', (e) => {
        e.preventDefault();
        registerForm.classList.add('hidden');
        loginForm.classList.remove('hidden');
        authSubtitle.textContent = 'Welcome back! Please login.';
        messageEl.style.display = 'none';
    });

    function showMsg(text, isSuccess) {
        messageEl.textContent = text;
        messageEl.className = 'message ' + (isSuccess ? 'success' : 'error');
        messageEl.style.display = 'block';
    }

    // Login Submit
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;

        try {
            const res = await fetch('/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();

            if (res.ok) {
                localStorage.setItem('token', data.token);
                localStorage.setItem('username', data.username);
                localStorage.setItem('role', data.role);
                showMsg('Login successful! Redirecting...', true);
                setTimeout(() => window.location.href = 'index.html', 1000);
            } else {
                showMsg(data.error || 'Login failed', false);
            }
        } catch (err) {
            showMsg('Connection error', false);
        }
    });

    // Register Submit
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('reg-username').value;
        const email = document.getElementById('reg-email').value;
        const password = document.getElementById('reg-password').value;

        try {
            const res = await fetch('/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password })
            });
            const data = await res.json();

            if (res.ok) {
                showMsg(data.message, true);
                setTimeout(() => showLogin.click(), 2000);
            } else {
                showMsg(data.error || 'Registration failed', false);
            }
        } catch (err) {
            showMsg('Connection error', false);
        }
    });
});

# 🛒 NextStore

NextStore is a full-stack e-commerce application developed as part of my backend internship at **Mercurial Minds**. The project focuses on implementing industry-standard backend technologies, scalable architecture, and production-ready features commonly used in modern applications.

---

## 🚀 Features

### 🔐 Authentication & Security

* JWT-based Authentication
* Role-Based Access Control (Admin, Customer, etc.)
* Password Encryption
* Rate Limiting to prevent abuse

### 🛍️ E-Commerce

* Product Management (CRUD)
* Order Management
* Category Management
* Shopping Cart
* Pagination
* Search & Filtering

### 💳 Payments

* Stripe Checkout Integration
* Subscription Support
* Secure Payment Processing

### 🎥 Video Processing

* Upload Videos
* Generate Multiple Video Qualities (360p, 480p, 720p)
* Automatic Thumbnail Generation
* FFmpeg Integration

### ⚡ Performance

* Redis Caching
* Reduced Database Queries
* Faster API Response Times

### 📬 Messaging

* RabbitMQ Integration
* Producer-Consumer Architecture
* Asynchronous Processing

### 📊 Analytics

* API Usage Analytics
* Real-Time API Monitoring
* Interactive D3.js Charts

### 🗄️ Database

* Master-Replica (Read/Write) Database Architecture
* SQL Server
* Optimized Queries

---

# 🛠️ Tech Stack

### Backend

* Java
* Spring Boot
* Spring Security
* Spring Data JPA
* Maven

### Database

* Microsoft SQL Server

### Authentication

* JWT (JSON Web Token)

### Cache

* Redis

### Messaging

* RabbitMQ

### Payments

* Stripe API

### Video Processing

* FFmpeg

### Containerization

* Docker

### Frontend

* HTML
* CSS
* JavaScript

### Visualization

* D3.js

---

# 🏗️ System Architecture

Client

⬇

Spring Boot REST APIs

⬇

JWT Authentication

⬇

Redis Cache

⬇

RabbitMQ (Async Tasks)

⬇

SQL Server (Master)

⬇

SQL Server (Replica)

⬇

FFmpeg Video Processing

⬇

Analytics Dashboard

---

# 📌 Major Functionalities

* User Registration & Login
* Secure JWT Authentication
* Product CRUD Operations
* Order Placement
* Stripe Payments
* Subscription Management
* Product Pagination
* Redis Caching
* RabbitMQ Messaging
* Video Upload & Processing
* Automatic Thumbnail Generation
* API Analytics Dashboard
* Rate Limiting
* Multi-Role Authorization

---

# ⚙️ Getting Started

## Prerequisites

* Java 21+
* Maven
* Docker Desktop
* SQL Server
* Redis
* RabbitMQ

---

## Clone Repository

```bash
git clone https://github.com/Fahadshoaib1068/NexStore.git
```

```bash
cd NexStore
```

---

## Run Docker Services

```bash
docker compose up -d
```

or run individual containers for:

* Redis
* RabbitMQ
* FFmpeg

---

## Configure Application

Update your `application.properties` file with:

* SQL Server credentials
* Redis configuration
* RabbitMQ configuration
* JWT Secret
* Stripe Keys

---

## Run Application

```bash
mvn spring-boot:run
```

---


---

## ⭐ Support

If you found this project helpful, consider giving it a ⭐ on GitHub.

# ⚡ SmartSeckill — AI-Powered High-Concurrency Seckill Mall

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.4-blue" alt="Vue 3">
  <img src="https://img.shields.io/badge/Redis-Lua%20Atomic-red" alt="Redis">
  <img src="https://img.shields.io/badge/RabbitMQ-Async-orange" alt="RabbitMQ">
  <img src="https://img.shields.io/badge/SQL%20Server-MyBatis%20Plus-yellow" alt="SQL Server">
  <img src="https://img.shields.io/badge/License-MIT-green" alt="License">
</p>

**SmartSeckill** is a full-stack, AI-powered high-concurrency flash sale (seckill) e-commerce platform. It demonstrates production-grade architecture for handling massive concurrent traffic — from Redis-based atomic inventory deduction and RabbitMQ asynchronous order processing to an AI risk control engine, bot army simulation, and real-time SSE push notifications.

---

## ✨ Features

### 🔥 Core Seckill Engine
- **Redis Lua Atomic Lock**: Token validation + duplicate check + inventory deduction + user lock — all in a single atomic Lua script, eliminating race conditions
- **RabbitMQ Async Order Creation**: Peak shaving & valley filling — only successful inventory deductions enter MQ, protecting the database from overload
- **Seckill Token System**: Anti-replay tokens to prevent repeated submissions
- **Real-Time SSE Push**: Live stock changes, competitor count, winner announcements via Server-Sent Events
- **Dual-Path Fallback**: MQ-first async ordering with synchronous database fallback when MQ is unavailable

### 🛡️ AI Risk Control Engine
- **Weighted Scoring Model** (100-point scale): Evaluates 8 behavioral dimensions in real time
  - Request frequency, inhuman speed detection, token-to-execute interval
  - IP multi-account detection, UA switching, activity hopping
  - New account detection, abnormal success rate analysis
- **4-Tier Disposition**: PASS → DELAY → VERIFY → BLOCK
- **Sliding Window Analysis**: Redis ZSet-based time-window feature extraction

### 🤖 AI Bot Army
- **Simulated Competition**: Bot users follow complete seckill flow (token → execute) consuming real inventory
- **Poisson Distribution Arrival**: Realistic arrival patterns within configurable time windows
- **Speed Profiles**: FAST / NORMAL / SLOW bots with configurable ratios
- **Live Leaderboard**: Bot wins broadcast via SSE for real-time atmosphere

### 📊 AI Seckill Assistant
- **Success Rate Prediction**: Competition index analysis based on online users vs remaining stock
- **Strategy Recommendations**: Personalized tips based on user history and activity conditions
- **Activity Heat Analysis**: Real-time hotness dashboard with sell-through rates

### 🛒 E-Commerce Features
- **Product System**: SPU/SKU model with categories, brands, and Elasticsearch full-text search
- **Shopping Cart**: Add/remove/update items with Redis caching
- **Order Management**: Create, pay, cancel with RabbitMQ delayed-queue timeout handling
- **Coupon System**: Admin-issued coupons with user collection and usage
- **Review System**: Nested reply comments (楼中楼 / Reddit-style threading)
- **User System**: JWT-based authentication with refresh tokens, role-based access control (Admin/User)

### 🖥️ Admin Dashboard
- Product management, order management, activity management
- User management, coupon management
- Real-time monitoring dashboard

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                         Frontend (Vue 3)                         │
│  Element Plus  │  Pinia  │  Axios  │  SSE Client  │  Vite       │
└──────────────────────────┬───────────────────────────────────────┘
                           │ HTTP / SSE
┌──────────────────────────▼───────────────────────────────────────┐
│                   Spring Boot 2.7 (Java 11)                      │
│                                                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │
│  │ Seckill  │ │   Risk   │ │ Assistant│ │    Bot Army      │   │
│  │  Engine  │ │ Control  │ │   AI     │ │   Simulator      │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────────┬─────────┘   │
│       │             │            │                 │             │
│  ┌────▼─────────────▼────────────▼─────────────────▼────────┐   │
│  │                    Redis (Lua Atomic)                     │   │
│  │   Stock │ Lock │ Token │ Features │ Online │ Cache        │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │                    RabbitMQ (Async)                       │   │
│  │   Seckill Orders │ Order Timeout (Delayed Queue)          │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │              SQL Server + Elasticsearch                    │   │
│  │   Users │ Products │ Orders │ Reviews │ Coupons           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  MinIO (Object Storage)  │  Caffeine (Local Cache)        │   │
│  │  Spring Security + JWT   │  Micrometer + Prometheus       │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### Seckill Flow (Hot Path)

```
User Request → @RateLimit → Risk Evaluate → Lua Atomic Lock
                                                    │
                    ┌───────────────────────────────┤
                    │ Token Invalid / Sold Out      │ Lock Acquired
                    ▼                               ▼
              Return Error                RabbitMQ Message (Async)
                                                    │
                                          ┌─────────▼──────────┐
                                          │  Order Consumer     │
                                          │  (DB Order Create)  │
                                          └────────────────────┘
                                                    │
                                          SSE Push Result to User
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend Framework** | Spring Boot 2.7.18 |
| **Language** | Java 11 |
| **ORM** | MyBatis-Plus 3.5 |
| **Database** | SQL Server |
| **Cache** | Redis (Lettuce) + Caffeine |
| **Message Queue** | RabbitMQ |
| **Search** | Elasticsearch |
| **Security** | Spring Security + JWT (jjwt 0.11) |
| **Object Storage** | MinIO |
| **Distributed Lock** | Redis Lua Scripts |
| **Frontend** | Vue 3 + Vite |
| **UI Library** | Element Plus |
| **State Management** | Pinia |
| **HTTP Client** | Axios |
| **Monitoring** | Micrometer + Prometheus + Actuator |
| **API Docs** | SpringDoc OpenAPI (Swagger) |

---

## 🚀 Quick Start

### Prerequisites

- **JDK 11+**
- **Maven 3.6+**
- **SQL Server** (or use Docker)
- **Redis** 6+
- **RabbitMQ** 3.9+
- **Node.js** 18+ (for frontend)
- **MinIO** (optional, for file uploads)

### 1. Clone the Repository

```bash
git clone https://github.com/twsxxn8/SmartSeckill.git
cd SmartSeckill
```

### 2. Configure Database & Middleware

Copy and edit the configuration:

```bash
cp src/main/resources/application.yml src/main/resources/application-dev.yml
```

Edit `application-dev.yml` with your actual connection settings:

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=smartseckill;encrypt=true;trustServerCertificate=true
    username: sa
    password: your_password
  redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### 3. Initialize Database

Run the SQL scripts (if provided in `docs/sql/`) to create the database schema, or let MyBatis-Plus auto-create tables on first run.

### 4. Start Backend

```bash
mvn clean package -DskipTests
java -jar target/smartseckill-1.0.0.jar
```

The API server starts at `http://localhost:8080/api`.

Swagger UI: `http://localhost:8080/api/swagger-ui.html`

### 5. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend dev server starts at `http://localhost:5173`.

---

## 📁 Project Structure

```
SmartSeckill/
├── src/main/java/com/smartseckill/
│   ├── ai/              # AI Assistant (success prediction, heat analysis)
│   ├── admin/           # Admin management controllers
│   ├── bot/             # Bot army simulator
│   ├── cart/            # Shopping cart module
│   ├── common/          # Shared config, exceptions, utils, AOP
│   ├── coupon/          # Coupon module
│   ├── order/           # Order module (with timeout consumer)
│   ├── product/         # Product SPU/SKU, search (Elasticsearch)
│   ├── review/          # Nested reply review system
│   ├── risk/            # AI risk control engine
│   ├── seckill/         # Core seckill engine (Lua, MQ, SSE)
│   └── user/            # Auth, user profile, addresses
├── src/main/resources/
│   ├── application.yml          # Default config (template, safe to commit)
│   └── application-dev.yml      # Dev secrets (gitignored)
├── frontend/
│   └── src/
│       ├── api/          # API request modules
│       ├── components/   # Reusable Vue components
│       ├── views/        # Page components (including /admin)
│       ├── stores/       # Pinia state stores
│       ├── router/       # Vue Router config
│       └── utils/        # SSE client, dayjs setup
└── pom.xml
```

---

## 🔑 Key Design Decisions

### Why Redis Lua Atomic Lock?

Traditional seckill solutions use 3+ independent Redis calls (validate token → check duplicate → deduct stock). Between these calls, race conditions allow concurrent requests from the same user to slip through. SmartSeckill combines all 4 steps into a **single Lua script** executed atomically in Redis's single thread — eliminating the race window entirely.

### Why RabbitMQ Async + Sync Fallback?

Seckill's bottleneck is the database (row-lock contention). By pre-validating inventory in Redis (100k+ QPS), only successful requests enter MQ. MQ consumers process at a rate the database can handle (~1k TPS). A synchronous fallback ensures orders are created even if MQ is temporarily unavailable.

### Why AI Bot Army?

Real seckill events feel empty without competition. The bot army simulates realistic human-like participants with configurable speed profiles and Poisson-distributed arrival times — consuming real inventory so users experience genuine competition pressure.

---

## 📊 Monitoring

Prometheus metrics are exposed at `/api/actuator/prometheus`. Use Grafana to visualize:

- Seckill request rate & success rate
- Redis cache hit ratio
- RabbitMQ queue depth
- Database connection pool usage
- JVM memory & GC stats

---

## 🤝 Contributing

Contributions are welcome! Please follow the [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/) branching strategy:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'feat: add some feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

### Commit Convention

- `feat:` New feature
- `fix:` Bug fix
- `refactor:` Code refactoring
- `docs:` Documentation changes
- `chore:` Build/config changes

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

---

## 🙏 Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot)
- [MyBatis-Plus](https://baomidou.com/)
- [Element Plus](https://element-plus.org/)
- [Redis](https://redis.io/)
- [RabbitMQ](https://www.rabbitmq.com/)

---

<p align="center">
  <b>Made with ❤️ by <a href="https://github.com/twsxxn8">twsxxn8</a></b>
</p>

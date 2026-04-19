# StreamVault: Real-Time Financial Ledger

**Status: Work in Progress (Planning Phase)**

**StreamVault** is a banking-grade, real-time financial ledger system designed to demonstrate enterprise-level backend architecture patterns. Built around the principles of Command Query Responsibility Segregation (CQRS) and Event Sourcing, this project models financial operations as an immutable stream of events rather than traditional database updates.

_Developed by Manuranga Jayawardhana._

---

## Project Overview

Unlike conventional CRUD applications, StreamVault never updates or deletes records in its primary data store. Instead, every user action—such as opening an account, depositing, withdrawing, or transferring funds—is recorded as a fact that happened at a specific point in time.

This architecture enables powerful capabilities:

- **Time-Travel Debugging:** Reconstructing state to answer queries like "What was this account's balance last Tuesday at 3pm?".
- **Real-Time Push:** Pushing live balance updates directly to connected browser clients via WebSockets.
- **Independent Scaling:** Completely separating the write path and read path into independent services with no shared database.

## High-Level Architecture

The system is built entirely around CQRS and Event Sourcing. The core principle is that the Command Service never answers queries, the Query Service never writes data, and Apache Kafka acts as the sole bridge between them.

- **Command Service (Write Side):** Validates business rules and appends domain events to a PostgreSQL event store. It publishes these events to the Kafka `transaction.events` topic.
- **Query Service (Read Side):** Consumes Kafka events to build read-optimised projections independently. It stores balance projections in PostgreSQL and Redis, and indexes transactions in Elasticsearch for full-text search.
- **WebSocket Gateway:** Subscribes to the event stream and pushes real-time updates to browser clients via STOMP over SockJS, eliminating the need for polling.
- **Frontend:** A server-rendered Next.js application using Redux Toolkit to manage global state and seamlessly update the UI as WebSocket events arrive.

## Technology Stack

**Backend Services**

- **Language & Framework:** Java 21 (LTS) with Spring Boot 3.3
- **Messaging:** Apache Kafka 3.7 & Spring Cloud Stream 4.x
- **Security:** Spring Security with stateless JWT validation
- **ORM:** Hibernate (JPA) 6.x

**Data Stores**

- **Primary Database:** PostgreSQL 14 (Event Store and Projections)
- **Caching & Sessions:** Redis 7
- **Search & Analytics:** Elasticsearch 8.x

**Frontend**

- **Framework:** Next.js 14 (App Router)
- **Styling & State:** Tailwind CSS 3.x and Redux Toolkit 2.x
- **Real-Time:** SockJS + STOMP

**Infrastructure & Observability**

- **Deployment:** Docker & Docker Compose
- **Metrics & Tracing:** Micrometer, Prometheus, Grafana 10.x, and Zipkin 3.x

## Development Roadmap

This project is currently in the initial stages and is being developed across five distinct, independent phases:

- [ ] **Phase 1: Event Store & Command Service** - Building the append-only PostgreSQL event store, the command-side API, and JWT authentication.
- [ ] **Phase 2: Query Service & CQRS Projection** - Implementing the read side, Kafka consumers, and Redis caching.
- [ ] **Phase 3: WebSocket Gateway & Real-Time Push** - Setting up STOMP/SockJS to eliminate polling and push live UI updates.
- [ ] **Phase 4: Elasticsearch Integration** - Indexing transactions for sub-10ms full-text search and spending analytics.
- [ ] **Phase 5: Observability Stack** - Wiring up Micrometer, Prometheus, Grafana, and Zipkin for distributed tracing and system health visibility.

---

_Note: This README serves as a blueprint for the StreamVault architecture and will be updated continuously as the project progresses through its development phases._

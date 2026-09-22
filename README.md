# SWEN3 - Paperless

### Philip Duong & Patrick Hornek

A document management and processing platform consisting of a Spring Boot (Kotlin) backend and a Next.js (TypeScript) web interface, backed by PostgreSQL and Elasticsearch.

---

## Getting Started Locally

### Prerequisites

- **[Docker](https://docs.docker.com/get-docker/)** & **[Docker Compose](https://docs.docker.com/compose/)** (v2.24+)

*(Optional for running services natively without Docker: Java 25+, Gradle, Node.js 22+, and pnpm 12+)*

---

### 1. Configure Environment Variables

Create your local environment configuration file from the template:

```bash
cp .env.example .env.local
```

Adjust any values (such as credentials, ports, or passwords) in `.env.local` as needed. All services in Docker Compose read from this file.

---

### 2. Start the Stack with Docker Compose

Build and launch all containers in detached mode:

```bash
docker compose --env-file .env.local up --build -d
```

This starts:
- **`backend`**: Spring Boot REST API
- **`ui`**: Next.js frontend application
- **`db`**: PostgreSQL 16 database
- **`elasticsearch`**: Elasticsearch 8.17.0 single-node cluster

---

### 3. Service Endpoints

Once all services are healthy and running:

| Service | URL | Description |
| :--- | :--- | :--- |
| **Frontend UI** | [http://localhost:3000](http://localhost:3000) | Next.js user interface |
| **Backend REST API** | [http://localhost:8080](http://localhost:8080) | Spring Boot backend |
| **Swagger UI** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Interactive OpenAPI documentation |
| **OpenAPI v3 Spec** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | Raw OpenAPI JSON definition |
| **Elasticsearch** | [http://localhost:9200](http://localhost:9200) | Search engine cluster endpoint |
| **PostgreSQL** | `localhost:5432` | Relational database (user/pass in `.env.local`) |

---

### 4. Viewing Logs & Status

Check running container status:
```bash
docker compose --env-file .env.local ps
```

Follow container logs:
```bash
docker compose --env-file .env.local logs -f
```

---

### 5. Stopping the Stack

Stop all running containers:
```bash
docker compose --env-file .env.local down
```

To stop and remove all volumes (including database and Elasticsearch data):
```bash
docker compose --env-file .env.local down -v
```

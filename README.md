# MindIO Backend API

Spring Boot backend for MindIO, a personal workspace for turning input into output. This is the API-only backend; the web UI lives in a separate repo: [mindio-web](https://github.com/mindio-me/mindio-web).

Live demo: https://demo.mindio.me

## Tech Stack

- Spring Boot 3.2
- Java 17
- Spring Security + JWT
- Spring Data JPA / Hibernate
- MySQL for server deployments
- Maven

## Development

```powershell
.\mvnw.cmd spring-boot:run
```

The API is served at:

```text
http://localhost:8080/api
```

OpenAPI documentation:

```text
http://localhost:8080/api/swagger-ui.html
```

The default development database is MySQL `mindio_app`:

```text
jdbc:mysql://localhost:3306/mindio_app
```

Override it with `SPRING_DATASOURCE_URL`, `DB_USERNAME`, and `DB_PASSWORD` when needed. The older `worknotes` database name is reserved for the personal branch/database.

## Desktop App

MindIO is also available as a desktop app. No server to deploy or configure — it runs directly on your own computer, and your notes and data stay on your local machine.

## Frontend

This is an API-only backend with no bundled UI. The web frontend is a separate Nuxt.js app — see [mindio-web](https://github.com/mindio-me/mindio-web) for the frontend source, self-hosting instructions, and one-click deploy.

## Naming

Some internal package names and configuration keys still use `worknotes` for compatibility. New product deployments should use the `mindio_app` database, while the older `worknotes` database name is reserved for personal/legacy use. User-facing product content should use `MindIO`.

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get started. Pull
requests require agreeing to our [Contributor License Agreement](CLA.md).

# MindIO Backend API

Spring Boot backend for MindIO, a personal workspace for turning input into output. This is the API-only backend; the web UI lives in a separate repo: [mindio-web](https://github.com/mindio-me/mindio-web).

Live demo: https://demo.mindio.me
Desktop app (macOS & Windows): https://mindio.me/download

## Tech Stack

- Spring Boot 3.2
- Java 17
- Spring Security + JWT
- Spring Data JPA / Hibernate
- MySQL for server deployments
- H2 file database for the desktop profile
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

Don't want to build this from source? Download the native macOS/Windows app instead — it bundles
this backend and runs everything locally, no deployment required.

**[Download MindIO Desktop →](https://mindio.me/download)**

The desktop app requires a subscription to activate: **$4.99/year** (early-bird price), billed
through Payhip.

[![Subscribe on Payhip](https://mindio.me/images/payhip-badge.png)](https://payhip.com/b/mindio-desktop)

## Desktop Profile

The Electron app starts this backend with:

```text
SPRING_PROFILES_ACTIVE=desktop
```

The desktop profile uses H2 and stores uploads in the local app data directory provided by Electron.

## Naming

Some internal package names and configuration keys still use `worknotes` for compatibility. New product deployments should use the `mindio_app` database, while the older `worknotes` database name is reserved for personal/legacy use. User-facing product content should use `MindIO`.

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get started. Pull
requests require agreeing to our [Contributor License Agreement](CLA.md).

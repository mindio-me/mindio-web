# MindIO

> Your personal workspace for turning input into output.

MindIO is a self-hostable personal workspace that combines note-taking, local resources, AI
analysis, and multi-platform publishing into one system.

```text
personal input -> MindIO -> personal output
```

Capture notes, documents, images, media, AI conversations, and web clips. MindIO helps you
organize and refine them, then publish the result to your personal website, articles, project
pages, or social media.

Website: https://mindio.me
Live demo: https://demo.mindio.me
Desktop app (macOS & Windows): https://mindio.me/download

This repository is a monorepo with three components:

- [`frontend/`](frontend/) — the web UI (Nuxt.js)
- [`server/`](server/) — the API backend (Spring Boot)
- [`agents/`](agents/) — the AI agent service (Python / LangGraph)

## Features

- **Notes & Knowledge Base** - rich text and Markdown editing, tags, search, and review
- **Local Resources** - collect local documents, images, audio, and video into your workspace
- **AI Chat & Agent Tools** - a docked AI assistant that can search, read, and write directly into your notes
- **Personal Website** - publish selected output to a public-facing site
- **Social Publishing** - push content to Reddit, WeChat, and more
- **Self-hostable** - run it on your own server, full control over your data

## Tech Stack

- Frontend: Nuxt.js 2.x, Vue.js 2.x, Element UI
- Backend: Spring Boot 3.2, Java 17, Spring Security + JWT, Spring Data JPA / Hibernate, MySQL
- Agent service: Python, FastAPI, LangGraph

## One-Click Deploy

[![Deploy on Railway](https://railway.app/button.svg)](https://railway.com/deploy/SfA56e?referralCode=GbJJAR)

## Self-Hosting

```bash
git clone https://github.com/mindio-me/mindio-web.git
cd mindio-web
cp .env.example .env
docker compose up -d
```

MindIO will be available at `http://localhost` or your configured domain.

If you're running behind an existing reverse proxy on the host (rather than exposing the `web`
container directly on 80/443), set `PORT` in `.env` to an internal port and proxy to it.

## Desktop App

Don't want to run a server at all? Download the native macOS/Windows app instead — everything
runs locally, no deployment required.

**[Download MindIO Desktop →](https://mindio.me/download)**

The desktop app requires a subscription to activate: **$4.99/year** (early-bird price), billed
through Payhip.

[![Subscribe on Payhip](https://mindio.me/images/payhip-badge.png)](https://payhip.com/b/mindio-desktop)

## Development

Each component has its own dev setup — see [`frontend/README.md`](frontend/README.md),
[`server/README.md`](server/README.md), and [`agents/README.md`](agents/README.md). To run the
full stack locally you'll typically want all three running at once against a shared MySQL
database.

## License

Licensed under the [GNU AGPL v3.0](LICENSE).

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get started. Pull
requests require agreeing to our [Contributor License Agreement](CLA.md).

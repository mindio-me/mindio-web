<div align="center">

<img src="https://mindio.me/brand/mindio-lockup.svg" alt="Mindio" height="56">

# Mio — your personal AI agent

**Your AI. Your Agent. Your Way.**

Meet Mio, your personal AI agent. Built around your knowledge, your workflows, and your needs.
Private, customizable, truly yours.

[![Release](https://img.shields.io/github/v/release/mindio-me/mindio-web?filter=desktop-v*&label=desktop)](https://github.com/mindio-me/mindio-web/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/mindio-me/mindio-web/total)](https://github.com/mindio-me/mindio-web/releases)
[![License: AGPL v3](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Deploy on Railway](https://railway.app/button.svg)](https://railway.com/deploy/SfA56e?referralCode=GbJJAR)

[Website](https://mindio.me) · [Download](#download-the-desktop-app) · [Live demo](https://demo.mindio.me) · [中文](README.zh-CN.md)

<img src="https://mindio.me/images/mindio-workspace-hero.png" alt="Mio working inside a note" width="860">

</div>

## Download the desktop app

| | |
|---|---|
| **Windows** (10 or later, 64-bit) | [⬇ Mindio-win-x64.exe](https://github.com/mindio-me/mindio-web/releases/latest/download/Mindio-win-x64.exe) |
| **macOS — Apple Silicon** (M1 or later) | [⬇ Mindio-mac-arm64.dmg](https://github.com/mindio-me/mindio-web/releases/latest/download/Mindio-mac-arm64.dmg) |
| **macOS — Intel** | Coming soon |
| **No install** | [Try Mio in your browser →](https://demo.mindio.me) |

Everything runs on your machine · bring your own model key · $4.99/year to activate · monthly plans coming soon.
The installer is free to download; activation is sold on [mindio.me](https://mindio.me/download).

**First launch on macOS:** the app isn't notarized yet, so macOS blocks it the first time. Control-click Mindio in
Applications and choose **Open**. Still blocked? Go to **System Settings → Privacy & Security** and click
**Open Anyway** (on macOS 15 and later this is the only way).
**On Windows:** if "Windows protected your PC" appears, click **More info → Run anyway**.

## What Mio can do

- **Recordings become notes** — record a meeting or a lecture; Mio transcribes it, tells speakers apart and pulls out
  key points, chapters and to-dos. Click any timestamp to hear that moment again.
- **Research a topic** — Mio reads your own notes and clips first, searches the web when it needs to, and writes a
  note with every source cited.
- **Write right inside your note** — Mio drafts, rewrites, inserts images and copies blocks across notes, in place.
  Every round of edits can be undone in one click.
- **Ask everything you've saved** — answers drawn from your notes, recordings and clips, with links back to the
  source.

It's also a notes app that just works: rich blocks (video, audio, galleries, math), day and month views, AI-sorted
clips, Markdown documents, share cards and public pages.

## Why Mindio

- **Your AI.** Notes stay on your machine (desktop) or your server (self-hosted). Mio runs on the model key you bring
  — you pay the provider directly, with no markup.
- **Your Agent.** Mio reads, writes and edits your notes, asks before adding to structured blocks, and every change
  can be undone.
- **Your Way.** Open source under AGPL-3.0. Self-host it, change it, make it fit how you work.

## Self-host in two minutes

```bash
git clone https://github.com/mindio-me/mindio-web.git
cd mindio-web
cp .env.example .env
docker compose up -d
```

Mindio will be available at `http://localhost` or your configured domain.

If you're running behind an existing reverse proxy on the host (rather than exposing the `web`
container directly on 80/443), set `PORT` in `.env` to an internal port and proxy to it.

Prefer one click? [![Deploy on Railway](https://railway.app/button.svg)](https://railway.com/deploy/SfA56e?referralCode=GbJJAR)

## Architecture

This repository is a monorepo with three components:

- [`frontend/`](frontend/) — the web UI (Nuxt.js 2, Vue 2, Element UI)
- [`server/`](server/) — the API backend (Spring Boot 3.2, Java 17, Spring Security + JWT, JPA / Hibernate, MySQL)
- [`agents/`](agents/) — the AI agent service behind Mio (Python, FastAPI, LangGraph)

## Development

Each component has its own dev setup — see [`frontend/README.md`](frontend/README.md),
[`server/README.md`](server/README.md), and [`agents/README.md`](agents/README.md). To run the
full stack locally you'll typically want all three running at once against a shared MySQL
database.

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get started. Pull
requests require agreeing to our [Contributor License Agreement](CLA.md).

## License

Licensed under the [GNU AGPL v3.0](LICENSE).

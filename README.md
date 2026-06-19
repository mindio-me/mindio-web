# MindIO

> Your personal workspace for turning input into output.

MindIO is a self-hostable personal workspace that combines note-taking, local resources, AI analysis, and multi-platform publishing into one system.

```text
personal input -> MindIO -> personal output
```

Capture notes, documents, images, media, AI conversations, and web clips. MindIO helps you organize and refine them, then publish the result to your personal website, articles, project pages, or social media.

Website: https://mindio.me

## Features

- **Notes & Knowledge Base** - rich text and Markdown editing, tags, search, and review
- **Local Resources** - collect local documents, images, audio, and video into your workspace
- **AI Analysis** - summarize, translate, extract insights, and reshape raw input
- **Personal Website** - publish selected output to a public-facing site
- **Social Publishing** - push content to Reddit, WeChat, and more
- **Self-hostable** - run it on your own server, full control over your data

## Tech Stack

- Nuxt.js 2.x
- Vue.js 2.x
- Element UI
- Axios

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

## Development

```bash
npm install
npm run dev
```

The frontend expects the API at:

```env
API_BASE_URL=http://localhost:8080/api
```

For containerized or one-click deployments, Docker builds the frontend with
`API_BASE_URL=/api`, which is proxied by nginx to the backend service. Set
`API_BASE_URL` only when the API is hosted on a different origin.

## Backend

The backend server is built with Java / Spring Boot.

## License

The frontend is licensed under the Apache License 2.0.

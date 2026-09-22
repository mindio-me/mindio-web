# MindIO Frontend

The web UI (Nuxt.js) for MindIO — see the [repository root README](../README.md) for the full
project overview, features, and self-hosting instructions.

The API it talks to lives in [`../server`](../server/).

## Tech Stack

- Nuxt.js 2.x
- Vue.js 2.x
- Element UI
- Axios

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

## License

Licensed under the [GNU AGPL v3.0](../LICENSE).

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](../CONTRIBUTING.md) for how to get started. Pull
requests require agreeing to our [Contributor License Agreement](../CLA.md).

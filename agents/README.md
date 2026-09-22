# MindIO Agent Service

The AI agent service for MindIO — see the [repository root README](../README.md) for the full
project overview, features, and self-hosting instructions.

Built with FastAPI and LangGraph. It talks to the backend at [`../server`](../server/) over an
internal API for reading and writing workspace content, and provides the model-facing tools
(search, note/media editing, web search, ASR) that back MindIO's AI chat feature.

## Tech Stack

- Python
- FastAPI
- LangGraph

## Development

```bash
python -m venv .venv
source .venv/bin/activate  # or .venv\Scripts\activate on Windows
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --port 8100
```

Run the test suite with:

```bash
pytest
```

## License

Licensed under the [GNU AGPL v3.0](../LICENSE).

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](../CONTRIBUTING.md) for how to get started. Pull
requests require agreeing to our [Contributor License Agreement](../CLA.md).

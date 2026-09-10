# Contributing

Thanks for your interest in improving this project.

## Development setup

1. Install Java 21 and Maven.
2. Start PostgreSQL locally.
3. Copy `.env.example` to `.env` and fill in your keys.
4. Run the backend:

```bash
mvn spring-boot:run
```

5. Run the frontend:

```bash
cd frontend
python -m http.server 5500
```

## Pull request guidelines

- Keep changes focused and easy to review.
- Add or update tests when behavior changes.
- Document new configuration or setup steps.
- Avoid committing secrets or real API keys.

## Coding conventions

- Follow the existing Java package layout.
- Keep frontend code simple and readable.
- Prefer clear naming and small, well-scoped changes.

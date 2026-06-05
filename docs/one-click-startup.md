# One-click startup

Use this option for local development on Windows.

Make sure Docker Desktop is running before starting the project.

Create a local `.env` file from `.env.example` and fill in the real API keys before starting:

```powershell
Copy-Item .env.example .env
```

Required for RAG with the existing knowledge vectors:

```text
LEXIFLOW_LLM_PROVIDER=deepseek
DEEPSEEK_API_KEY=your-deepseek-api-key
DASHSCOPE_API_KEY=your-dashscope-api-key
```

The knowledge base was embedded with DashScope `text-embedding-v4`. If the backend starts with the default mock provider, query vectors will not match the stored vectors and RAG retrieval will return no references.

## Start

From the repository root:

```powershell
.\start-dev.ps1
```

The script starts:

- PostgreSQL, Redis, and RabbitMQ through Docker Compose
- Spring Boot backend with the `dev` profile
- Vite frontend development server
- LLM environment variables loaded from local `.env`

## URLs

```text
Frontend:  http://localhost:5173
Backend:   http://localhost:8080/api
Health:    http://localhost:8080/api/actuator/health
RabbitMQ:  http://localhost:15672
Account:   admin / admin123
```

## PowerShell policy

If PowerShell blocks local scripts, run this once:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

## Stop

To stop Docker dependencies:

```powershell
.\stop-dev.ps1
```

The backend and frontend are launched in separate PowerShell windows so their logs remain visible. Close those windows to stop the Java and Node development servers.

# Spring AI Job Agent

Spring Boot application that exposes chat and job-application endpoints, including a job search tool (scraping via Playwright), resume tailoring, cover letter generation, and application tracking.

## Prerequisites
- Java 25
- Maven
- PostgreSQL (if using chat memory repository)
- Playwright browsers installed (see Setup)

## Setup
1. Configure `application.properties` as needed.
2. Install Playwright browsers:

```powershell
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
```

## Run
```powershell
mvn spring-boot:run
```

## Endpoints
- `POST /api/agent/chat`  
  Body:
  ```json
  { "message": "Search Java jobs in Hyderabad", "sessionId": "user-1" }
  ```
- `POST /api/chat?prompt=...`
- `POST /api/chat/structured`
- `POST /api/chat/flux`
- `POST /api/resume/upload` (multipart form field `file`)

## Notes
- The job search tool relies on live page scraping and may require selector updates if sites change.
- Tool calling depends on your model supporting tools.

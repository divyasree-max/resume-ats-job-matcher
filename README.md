# Resume ATS + Career Guide + Live Job Matcher

A Java/Spring Boot version of the ATS resume matcher with:
- resume upload and parsing
- live job description scoring
- ranked job search results
- a role-based career roadmap for Data Scientist, AI Engineer, Prompt Engineering, Generative AI, and custom roles
- a static frontend built with plain HTML, CSS, and JavaScript

## Tech stack

- Backend: Java 21, Spring Boot 3.3.2
- Database: PostgreSQL 16
- Resume parsing: Apache Tika + Apache PDFBox
- AI scoring: Google Gemini API
- Job search: Adzuna API
- Frontend: static HTML/CSS/JS served locally

## Project structure

```text
resume-ats-job-matcher/
  pom.xml
  README.md
  src/
    main/
      java/com/atsmatcher/
        AtsMatcherApplication.java
        config/WebConfig.java
        controller/
          ResumeController.java
          JobSearchController.java
        dto/
          ResumeDtos.java
          JobSearchDtos.java
        model/
          Resume.java
          JobListing.java
        repository/
          ResumeRepository.java
          JobListingRepository.java
        service/
          ResumeTextExtractionService.java
          ExperienceDetectionService.java
          LocationService.java
          SalaryService.java
          QualityFilterService.java
          DedupService.java
          JobSearchService.java
          GeminiScoringService.java
      resources/
        application.properties
  frontend/
    index.html
    style.css
    script.js
```

## Required setup

### 1) Start PostgreSQL

```bash
docker run --name ats-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=ats_matcher -p 5432:5432 -d postgres:16
```

### 2) Set required environment variables

```bash
$env:GEMINI_API_KEY="your_gemini_key"
$env:ADZUNA_APP_ID="your_adzuna_app_id"
$env:ADZUNA_APP_KEY="your_adzuna_app_key"
```

Optional database overrides:

```bash
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres"
```

### 3) Run the backend

```bash
cd resume-ats-job-matcher
mvn spring-boot:run
```

The backend runs on:

```text
http://localhost:8081
```

### 4) Run the frontend

```bash
cd frontend
python -m http.server 5500
```

Then open:

```text
http://localhost:5500
```

## App flow

- Upload a resume PDF/DOCX/DOC
- Review extracted experience and resume summary
- Paste or compare a job description
- Search and rank jobs against the uploaded resume
- Open the Career Guide and choose a target role such as AI Engineer, Prompt Engineering, Generative AI, or a custom role

## Configuration

The app settings are in:

```text
src/main/resources/application.properties
```

Important values:

- `server.port=8081`
- PostgreSQL URL: `jdbc:postgresql://localhost:5432/ats_matcher`
- Gemini key: `GEMINI_API_KEY`
- Adzuna keys: `ADZUNA_APP_ID`, `ADZUNA_APP_KEY`

## API endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/resumes/upload` | Upload a resume file and receive parsed details and a resume ID |
| POST | `/api/resumes/score-jd` | Compare a resume against a pasted job description |
| POST | `/api/jobs/search` | Search jobs, filter, rank, and score against the resume |

## Notes

- The project is designed to work with real Gemini and Adzuna credentials for stronger matching.
- If those keys are missing, the backend can still start and resume parsing can still work, but live scoring and job search will be limited or fall back to local logic.
- The frontend is intentionally simple and does not require a framework or build process.

## Current status

This repo is set up for a local working flow on a Windows machine with WSL/Docker support and Java/Maven installed. The verified end-to-end flow includes:

- Spring Boot backend running on port 8081
- PostgreSQL database running locally
- Resume upload working
- Job search API responding
- Career Guide navigation and custom role selection working in the frontend

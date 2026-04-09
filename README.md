# Secfix — Security Questionnaire Autofill Service

A Spring Boot RAG (Retrieval-Augmented Generation) service that automatically fills security questionnaires from your internal policy and compliance documents.

## How it works

1. **Ingest** your policy documents (PDF, DOCX, XLSX) — they are parsed, chunked, embedded, and stored in an in-memory vector store.
2. **Fill** an empty security questionnaire (XLSX) — the service infers the sheet structure, retrieves relevant context for each question, and writes compliant answers directly into the workbook, respecting dropdown constraints and text length limits.

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ (or use the included `./mvnw`) |
| OpenAI API key | — |

## Setup

### 1. Set the OpenAI API key

```bash
export OPENAI_API_KEY=sk-...
```

Or on Windows:

```cmd
set OPENAI_API_KEY=sk-...
```

### 2. Build

```bash
./mvnw clean package -DskipTests
```

### 3. Run

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

---

## API Reference

All endpoints require a `customer_id` query parameter that scopes ingested documents and questionnaire runs per tenant.

### Ingest a document

```
POST /api/documents?customer_id=<id>
Content-Type: multipart/form-data

file: <PDF | DOCX | XLSX>
```

```bash
curl -X POST "http://localhost:8080/api/documents?customer_id=acme" \
     -F "file=@security-policy.pdf"
```

Response:
```json
{ "documentId": 1, "chunkCount": 42, "message": "Document ingested successfully" }
```

### List ingested documents

```
GET /api/documents?customer_id=<id>
```

### Get / delete a document

```
GET    /api/documents/{id}?customer_id=<id>
DELETE /api/documents/{id}?customer_id=<id>
```

---

### Fill a questionnaire

```
POST /api/questionnaire/fill?customer_id=<id>
Content-Type: multipart/form-data

file: <empty XLSX questionnaire>
```

Returns the filled XLSX file as a download. The `X-Questionnaire-Run-Id` response header contains the run ID for status tracking.

```bash
curl -X POST "http://localhost:8080/api/questionnaire/fill?customer_id=acme" \
     -F "file=@questionnaire-empty.xlsx" \
     -o questionnaire-filled.xlsx
```

### List questionnaire runs

```
GET /api/questionnaire/runs?customer_id=<id>
```

### Get a specific run

```
GET /api/questionnaire/runs/{runId}?customer_id=<id>
```

Run status fields: `PENDING` → `IN_PROGRESS` → `COMPLETED` / `FAILED`.

---

## Configuration

All settings are in `src/main/resources/application.properties`.

| Property | Default                | Description |
|---|------------------------|---|
| `OPENAI_API_KEY` | *(required env var)*   | OpenAI API key |
| `openai.embedding.model` | `text-embedding-3-small` | Embedding model |
| `openai.completion.model` | `gpt-4o-mini`          | Completion model |
| `openai.completion.max-tokens` | `1024`                 | Max tokens per answer |
| `rag.chunker.chunk-size-tokens` | `500`                  | Chunk size for document splitting |
| `rag.chunker.overlap-tokens` | `50`                   | Overlap between chunks |
| `rag.retrieval.top-k` | `5`                    | Number of chunks retrieved per question |
| `rag.retrieval.min-score` | `0.75`                 | Minimum cosine similarity threshold |

---

## Quick start example

```bash
# 1. Export API key
export OPENAI_API_KEY=sk-...

# 2. Start the service
./mvnw spring-boot:run

# 3. Ingest a policy document
curl -X POST "http://localhost:8080/api/documents?customer_id=demo" \
     -F "file=@my-security-policy.pdf"

# 4. Fill the questionnaire
curl -X POST "http://localhost:8080/api/questionnaire/fill?customer_id=demo" \
     -F "file=@questionnaire-empty.xlsx" \
     -o questionnaire-filled.xlsx
```

---

## Data Model

The service uses an H2 in-memory database with the following schema. All tables carry a `customer_id` column for multi-tenant isolation.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  INGESTION SIDE                                                              │
├──────────────────┐   ┌──────────────────┐   ┌──────────────────────────────┤
│   documents      │   │ knowledge_chunks  │   │ vector_store                 │
│──────────────────│   │──────────────────│   │──────────────────────────────│
│ id (PK)          │◄──│ id (PK)          │◄──│ id (PK)                      │
│ title            │   │ document_id (FK) │   │ document_id (FK)             │
│ original_file... │   │ chunk_text       │   │ chunk_id (FK, unique)        │
│ customer_id      │   │ embedding_json   │   │ customer_id                  │
│ created_at       │   │ page_num         │   │ created_at                   │
└──────────────────┘   │ customer_id      │   └──────────────────────────────┘
                       └──────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│  QUESTIONNAIRE SIDE                                                          │
├──────────────────────────┐                                                  │
│   questionnaire_runs     │                                                  │
│──────────────────────────│                                                  │
│ id (PK)                  │                                                  │
│ run_id (unique UUID)     │                                                  │
│ input_file_name          │                                                  │
│ output_file_name         │                                                  │
│ status                   │  PENDING → IN_PROGRESS → COMPLETED / FAILED     │
│ total_fields             │                                                  │
│ answered_fields          │                                                  │
│ insufficient_context_... │                                                  │
│ failed_fields            │                                                  │
│ error_message            │                                                  │
│ customer_id              │                                                  │
│ started_at / completed.. │                                                  │
└──────────┬───────────────┘                                                  │
           │ 1:N                                                              │
           ▼                                                                  │
┌──────────────────────────┐                                                  │
│   questionnaire_items    │  one row per question (Excel row)                │
│──────────────────────────│                                                  │
│ id (PK)                  │                                                  │
│ run_id (FK)              │                                                  │
│ sheet_index / sheet_name │                                                  │
│ row_index                │                                                  │
│ question_text            │                                                  │
│ customer_id              │                                                  │
└──────────┬───────────────┘                                                  │
           │ 1:N                                                              │
           ▼                                                                  │
┌──────────────────────────┐                                                  │
│   questionnaire_fields   │  one row per answer column (Response, Comment…) │
│──────────────────────────│                                                  │
│ id (PK)                  │                                                  │
│ item_id (FK)             │                                                  │
│ column_index             │                                                  │
│ column_name              │                                                  │
│ cell_reference           │  e.g. B5                                         │
│ field_type               │  DROPDOWN | TEXT                                 │
│ is_constrained           │                                                  │
│ allowed_options_json     │  ["In place","Partially in place",…]             │
│ customer_id              │                                                  │
└──────────┬───────────────┘                                                  │
           │ 1:N (1 per attempt)                                              │
           ▼                                                                  │
┌──────────────────────────┐   ┌──────────────────────────────────────────┐  │
│   generated_answers      │   │   answer_evidence                        │  │
│──────────────────────────│   │──────────────────────────────────────────│  │
│ id (PK)                  │◄──│ id (PK)                                  │  │
│ run_id / item_id (FK)    │   │ answer_id (FK)                           │  │
│ field_id (FK)            │   │ knowledge_chunk_id (FK)                  │  │
│ attempt_no               │   │ rank_position                            │  │
│ answer_text              │   │ similarity_score                         │  │
│ answer_status            │   │ source_document_title                    │  │
│ confidence_score         │   │ source_page_num                          │  │
│ model_name               │   │ chunk_snippet                            │  │
│ prompt_version           │   │ customer_id                              │  │
│ customer_id              │   └──────────────────────────────────────────┘  │
└──────────────────────────┘                                                  │
```

### `answer_status` values

| Value | Meaning |
|---|---|
| `GENERATED` | Answer written to the workbook |
| `INSUFFICIENT_CONTEXT` | No relevant context found in ingested documents |
| `CONSTRAINT_VIOLATION` | LLM response did not match any allowed dropdown option |
| `ERROR` | Unexpected failure during generation |

---

## Development

### Run tests

```bash
./mvnw test
```

### H2 console (in-memory DB inspector)

Available at `http://localhost:8080/h2-console` while the service is running.
- JDBC URL: `jdbc:h2:mem:secfixdb`
- User: `sa` / Password: *(empty)*

> **Note:** All data (ingested documents, vector embeddings, questionnaire runs) is held in memory and is lost on restart.

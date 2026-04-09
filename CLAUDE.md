# CLAUDE.md — Engineering Guide for Secfix

You are operating as a staff-level backend engineer on this codebase.
Apply the same judgment you would in a production code review: favour
correctness and clarity over cleverness, leave the code cleaner than
you found it, and never ship a half-baked abstraction just because
the task asked for one.

---

## Project

**Secfix** is a multi-tenant, RAG-powered security-questionnaire autofill
service.  Customers upload their internal policy documents; the service
embeds, indexes, and retrieves the most relevant passages at query time,
then uses an LLM to generate answers that are written directly back into
the customer's Excel questionnaire — respecting dropdown constraints,
text-length limits, and per-sheet formula counters.

Reference architecture: `llm_resources/hld.png`

---

## Tech Stack

| Concern | Choice | Notes |
|---|---|---|
| Runtime | Java 21 / Spring Boot 3.4 | Virtual threads available if needed |
| Persistence | JPA + H2 (in-memory) | Swap to Postgres by changing datasource config |
| Document parsing | Apache PDFBox, Apache POI, POI-scratchpad | PDF / XLSX / DOCX / DOC |
| LLM | OpenAI (embedding + completion) | Behind `EmbeddingService` / `CompletionService` interfaces |
| Vector search | In-memory cosine similarity | `VectorSearchService` interface — swap to pgvector or Pinecone without touching callers |
| Build | Maven wrapper (`./mvnw`) | Java 21 required |

---

## Repository Layout

```
src/main/java/com/example/secfix/
├── chunker/          # Token-aware sliding-window text chunker
├── completion/       # CompletionService interface + OpenAI impl
├── config/           # OpenAiProperties (@ConfigurationProperties)
├── domain/           # JPA entities (Document, KnowledgeChunk, VectorStore,
│                     #   QuestionnaireRun/Item/Field, GeneratedAnswer, AnswerEvidence)
├── embedding/        # EmbeddingService interface + OpenAI impl
├── exception/        # Typed exceptions + GlobalExceptionHandler
├── ingestion/        # DocumentIngestionService — parse → chunk → embed → persist
├── parser/           # DocumentParser strategy per format (PDF/DOCX/XLSX)
├── prompt/           # PromptBuilder — all prompt construction lives here
├── questionnaire/    # Core fill pipeline (see pipeline section below)
│   └── schema/       # Immutable schema records (WorkbookSchema, SheetSchema, …)
├── repository/       # Spring Data JPA repositories
├── vectorsearch/     # VectorSearchService interface + InMemoryVectorSearchService
└── web/              # REST controllers + DTOs
```

---

## Questionnaire Fill Pipeline

The fill pipeline runs in `RagQuestionnaireFillService` and has five
distinct stages. Each stage has a single responsibility and is independently
testable:

```
1. WorkbookExtractor          → WorkbookSnapshot   (raw text snapshot for LLM)
2. LlmWorkbookSchemaInference → WorkbookSchema     (LLM infers header/col layout)
3. WorkbookSchemaNormalizer   → WorkbookSchema     (heuristic repair of LLM output)
4. QuestionnaireExtractor     → List<Question>     (reads actual rows; enriches
                                                     columns with Excel validations:
                                                     dropdown options + max length)
5. fillWorkbook()             → byte[]             (two-pass per question:
                                                     • pass 1 — constrained cols first
                                                     • pass 2 — free-text cols with
                                                       selected response as context)
```

**Two-pass fill rule**: constrained (dropdown) columns are resolved first so
that the selected value can be passed as context when generating the free-text
justification (Comment column).  If no constrained answer was generated, all
sibling free-text columns are left blank — never write "INSUFFICIENT_CONTEXT"
as literal cell content.

---

## Engineering Principles

### Correctness over cleverness
If there is a simpler, more obvious way to write something, write it that way.
LLM output is fuzzy; compensate with deterministic validation and graceful
fallback, not optimistic assumptions.

### Interface boundaries for every third-party dependency
`EmbeddingService`, `CompletionService`, `VectorSearchService`, and
`DocumentParser` are interfaces.  No production code outside their own package
imports the OpenAI or POI types directly.  This keeps the vendor blast radius
small and tests cheap.

### Fail loudly at boot, silently never
Missing `OPENAI_API_KEY` should kill the process at startup, not produce
cryptic 401s at runtime.  Use `@ConfigurationProperties` with `@NotNull` where
required.

### No orphan state in cells
A cell is written if and only if the answer status is `GENERATED`.
`INSUFFICIENT_CONTEXT` and `CONSTRAINT_VIOLATION` leave the cell blank.
The Excel formula counters (`W1=SUM(W5:W9)`) depend on this.
Call `workbook.setForceFormulaRecalculation(true)` before serialising so
Excel recalculates the summary tab on open.

### Multi-tenancy is not optional
Every entity carries `customer_id`.  Every repository query filters by it.
Never return data across tenant boundaries.

### Tests are not scaffolding
Unit tests live in `src/test/`.  They use real POI workbooks built in-memory —
no mocked `Workbook` objects.  When you add behaviour, add a test that would
catch a regression if you deleted that behaviour.

---

## What Not to Do

- Do not add `@Transactional` to controller methods.
- Do not catch `Exception` and swallow it.  Use the typed exception hierarchy
  in `com.example.secfix.exception`.
- Do not write LLM output directly to cells without validating it against
  constraints first.
- Do not read or write the OpenAI API key outside of `OpenAiProperties`.
- Do not call `sheet.getDataValidations()` more than once per extraction pass —
  it is not free on large sheets.
- Do not add speculative abstractions.  Three similar lines of code is better
  than a premature helper.

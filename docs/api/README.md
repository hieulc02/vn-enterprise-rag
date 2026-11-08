# API Integration & Contracts

This document outline how to interact with the InsightRAG retrieval API (`insightrag-retrieval`). The API is
secured via OAuth2 and documented using OpenAPI 3.1.

## Authentication & Security (Keycloak)

The backend acts as an OAuth2 Resource Server. All endpoints require a valid JSON Web Token (JWT) issued by the local
Keycloak instance. Spring security intercepts incoming requests to validate the token's
signature against Keycloak's JWKS endpoints and verifies the required role claims.

**Future UI Flow:** The intended frontend integration will utilize the **OAuth2 Authorization Code Flow with PKCE**,
delegating the login screen entirely to Keycloak.

**Local Testing Flow (Direct Grant):**
To test the API locally via Postman or `curl` without a UI, request a token directly from Keycloak using the password
grant type:

```bash
curl -X POST "http://localhost:8080/realms/insightrag/protocol/openid-connect/token" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "client_id=insightrag" \
     -d "username=testuser" \
     -d "password=testpassword" \
     -d "grant_type=password"
```

Extract the `access_token` from the response and inject it into the API requests as an `Authorization: Bearer <token>`
header.

## Rate Limiting & Quotas

To protect backend resources and LLM inference budgets, the chat endpoints (`/api/v1/chat` and `/api/v1/chat/stream`)
enforce user-level rate limiting based on the authenticated JWT subject.

Successful requests will include the following header indicating our current quota:

* `RateLimit-Remaining`: The number of tokens/requests we have left in the current time window.

**Handling Rate Limits (HTTP 429)**
If a user exhausts their quota, the API intercepts the request and immediately return an `HTTP 429 Too Many Requests`
status, bypassing the service layer entirely. The response will include a `Retry-After` header (in seconds) and a
standard `ProblemDetail` JSON payload detailing when we can resume requests.

**Example 429 Response:**

```http request
HTTP/1.1 429 Too Many Requests
Retry-After: 35
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "API quota has been exhausted. Please try again in 35 seconds.",
  "instance": "/api/v1/chat"
}
```

## OpenAPI & Swagger UI

The API contracts are generated dynamically. When the `insightrag-retrieval` module is running locally, we can access
the interactive documentation:

* **Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **OpenAPI JSON Spec:** `http://localhost:8080/v3/api-docs`

## Core Endpoint Examples

The API supports two parallel Chat interfaces depending on the client's UX requirements.

### 1. Synchronous Chat (`POST /api/v1/chat`)

Return the complete LLM response in a single JSON payload. Best suited for simple queries.

```bash
curl -X POST "http://localhost:8080/api/v1/chat" \
     -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
           "query": "Trong số các công ty con của tập đoàn Hòa Phát thuộc phần thuyết minh, công ty nào có sự biến động về giá gốc đầu tư giữa ngày 01/01/2026 và 31/03/2026, và khoản đầu tư này thuộc nhóm tài sản nào trên bảng cân đối kế toán?",
           "session_id": "client-session-id"
         }'
```

### 2. Streaming Chat (`POST /api/v1/chat/stream`)

Utilizes Server-Sent Event (SSE) to stream the LLM response token-by-token. This is the recommend endpoint for frontends
to prevent UI blocking during graph traversal and generation.

```bash
curl -N -X POST "http://localhost:8080/api/v1/chat/stream" \
     -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
     -H "Content-Type: application/json" \
     -H "Accept: text/event-stream" \
     -d '{
           "query": "Hãy xác minh tính hợp lệ của giá trị \"Tài sản cố định hữu hình\" (Mã số 221) tại ngày 31/03/2026 bằng cách sử dụng các dữ liệu về nguyên giá (Mã số 222) và giá trị hao mòn lũy kế (Mã số 223) tương ứng?",
           "session_id": "client-session-id"
         }'
```

Note: The `-N` flag in curl disables buffering to render the text stream in real-time. The server enforces a 120-second
timeout on the SSE connection and will emit standard `ProblemDetail` JSON on fatal errors. 
# Logging and Error Handling

## Structured logging

Backend 使用 Spring Boot 內建 structured logging，console 輸出採 ECS JSON。不得另行記錄 request 或 response 的 Token、Cookie、`Authorization` header、密碼或完整 Secret。

## Request ID

1. 接受符合安全字元限制、長度不超過 128 的 `X-Request-ID`。
2. Header 缺少或格式不安全時產生 UUID。
3. Request ID 寫入 MDC 的 `requestId`，由 ECS structured log 帶出。
4. Response 的 `X-Request-ID` 與錯誤 body 的 `requestId` 必須相同。
5. Request 結束後清除 MDC，避免 thread reuse 汙染後續請求。

## Error contract

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "requestId": "uuid",
  "timestamp": "2026-08-01T10:00:00Z",
  "path": "/api/example",
  "fieldErrors": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

`fieldErrors` 只在有欄位錯誤時提供。非預期錯誤回傳一般化訊息，不得包含 exception message、stack trace、SQL 或內部路徑；完整 exception 只留在受保護的 server log，並以 request ID 串接。

## Health endpoint

Backend 只暴露 `/actuator/health`。回應只提供整體狀態，不顯示 component、資料庫連線或環境細節。Frontend 的 Browser 只呼叫同源 `/api/backend-health`，由 Next.js server 使用 `BACKEND_INTERNAL_URL` 代理 Backend health。

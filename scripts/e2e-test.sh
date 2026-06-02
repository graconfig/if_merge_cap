#!/usr/bin/env bash
# 端到端测试脚本（Mock 模式）
# 用法：./e2e-test.sh [base_url]
# 默认 base_url: http://localhost:8080

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
TIMEOUT_SEC=60

# 颜色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; exit 1; }
info() { echo -e "${YELLOW}→${NC} $1"; }

# ────────────── 0. 健康检查 ──────────────
info "0. Health check ..."
STATUS=$(curl -s "$BASE_URL/actuator/health" | jq -r .status)
[ "$STATUS" = "UP" ] || fail "Health check failed: $STATUS"
ok "Service is UP"

# ────────────── 1. 提交解析 ──────────────
info "1. POST /api/v1/analyze ..."
ANALYZE_RES=$(curl -s -X POST "$BASE_URL/api/v1/analyze" \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "BDN-EPD-OF-093.xlsx",
    "sheets": [
      {
        "name": "表紙",
        "headers": ["項目","値"],
        "rows": [["文書管理番号","BDN-EPD-OF-093"],["IF名","受注ヘッダ連携"]]
      },
      {
        "name": "エクスポート項目",
        "headers": ["No","EBSテーブル名","EBSテーブルID","項目ID","項目名","桁数"],
        "rows": [
          ["1","受注ヘッダ","OE_ORDER_HEADERS_ALL","ORDER_NUMBER","注文番号","22"],
          ["2","受注ヘッダ","OE_ORDER_HEADERS_ALL","CUSTOMER_ID","顧客コード","10"],
          ["3","受注明細","OE_ORDER_LINES_ALL","LINE_NUMBER","明細番号","10"]
        ]
      }
    ],
    "options": { "phase1HeadRows": 30, "maxChunkRows": 100 }
  }')

ANALYZE_JOB_ID=$(echo "$ANALYZE_RES" | jq -r .id)
ANALYZE_STATUS=$(echo "$ANALYZE_RES" | jq -r .status)
[ "$ANALYZE_JOB_ID" != "null" ] || fail "No jobId returned"
[ "$ANALYZE_STATUS" = "PENDING" ] || fail "Initial status should be PENDING, got $ANALYZE_STATUS"
ok "Analyze Job created: $ANALYZE_JOB_ID"

# ────────────── 2. 轮询直到完成 ──────────────
info "2. Polling analyze status ..."
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT_SEC ]; do
  POLL=$(curl -s "$BASE_URL/api/v1/analyses/$ANALYZE_JOB_ID")
  STATUS=$(echo "$POLL" | jq -r .status)
  PROGRESS=$(echo "$POLL" | jq -r .progress)
  PHASE=$(echo "$POLL" | jq -r .phase)
  echo "   [$ELAPSED s] $STATUS ($PROGRESS%) $PHASE"
  if [ "$STATUS" = "SUCCEEDED" ]; then break; fi
  if [ "$STATUS" = "FAILED" ]; then fail "Analyze FAILED: $(echo "$POLL" | jq -r .error)"; fi
  sleep 1; ELAPSED=$((ELAPSED+1))
done
[ "$STATUS" = "SUCCEEDED" ] || fail "Timeout after ${TIMEOUT_SEC}s"
ok "Analyze SUCCEEDED in ${ELAPSED}s"

# ────────────── 3. 取解析结果 ──────────────
info "3. GET /api/v1/analyses/{id}/result ..."
ANALYZE_RESULT=$(curl -s "$BASE_URL/api/v1/analyses/$ANALYZE_JOB_ID/result")
RECORD_COUNT=$(echo "$ANALYZE_RESULT" | jq '.result.records | length')
DOC_NUMBER=$(echo "$ANALYZE_RESULT" | jq -r .result.documentNumber)
IF_NAME=$(echo "$ANALYZE_RESULT" | jq -r .result.ifName)
[ "$RECORD_COUNT" -gt 0 ] || fail "No records in result"
ok "Got $RECORD_COUNT records (doc=$DOC_NUMBER, if=$IF_NAME)"

# ────────────── 4. 提交合并 ──────────────
info "4. POST /api/v1/merge ..."
RECORDS_JSON=$(echo "$ANALYZE_RESULT" | jq .result.records)
MERGE_RES=$(curl -s -X POST "$BASE_URL/api/v1/merge" \
  -H "Content-Type: application/json" \
  -d "{
    \"records\": $RECORDS_JSON,
    \"options\": { \"threshold\": 0.80, \"mode\": \"max\" }
  }")
MERGE_JOB_ID=$(echo "$MERGE_RES" | jq -r .id)
[ "$MERGE_JOB_ID" != "null" ] || fail "No merge jobId returned"
ok "Merge Job created: $MERGE_JOB_ID"

# ────────────── 5. 轮询合并 ──────────────
info "5. Polling merge status ..."
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT_SEC ]; do
  POLL=$(curl -s "$BASE_URL/api/v1/merges/$MERGE_JOB_ID")
  STATUS=$(echo "$POLL" | jq -r .status)
  PROGRESS=$(echo "$POLL" | jq -r .progress)
  PHASE=$(echo "$POLL" | jq -r .phase)
  echo "   [$ELAPSED s] $STATUS ($PROGRESS%) $PHASE"
  if [ "$STATUS" = "SUCCEEDED" ]; then break; fi
  if [ "$STATUS" = "FAILED" ]; then fail "Merge FAILED: $(echo "$POLL" | jq -r .error)"; fi
  sleep 1; ELAPSED=$((ELAPSED+1))
done
[ "$STATUS" = "SUCCEEDED" ] || fail "Merge timeout after ${TIMEOUT_SEC}s"
ok "Merge SUCCEEDED in ${ELAPSED}s"

# ────────────── 6. 取合并结果 ──────────────
info "6. GET /api/v1/merges/{id}/result ..."
MERGE_RESULT=$(curl -s "$BASE_URL/api/v1/merges/$MERGE_JOB_ID/result")
TOTAL_IF=$(echo "$MERGE_RESULT" | jq .summary.totalInterfaces)
TOTAL_GROUPS=$(echo "$MERGE_RESULT" | jq .summary.totalGroups)
MERGEABLE=$(echo "$MERGE_RESULT" | jq .summary.mergeableGroups)
ok "Merge summary: $TOTAL_IF IFs → $TOTAL_GROUPS groups ($MERGEABLE mergeable)"

# ────────────── 7. 错误场景：404 ──────────────
info "7. Error case: GET non-existent job ..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "$BASE_URL/api/v1/analyses/00000000-0000-0000-0000-000000000000")
[ "$HTTP_CODE" = "404" ] || fail "Expected 404 for non-existent job, got $HTTP_CODE"
ok "404 returned for non-existent job"

# ────────────── 8. 错误场景：400 ──────────────
info "8. Error case: POST with empty body ..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "$BASE_URL/api/v1/analyze" \
  -H "Content-Type: application/json" -d '{}')
[ "$HTTP_CODE" = "400" ] || fail "Expected 400 for empty body, got $HTTP_CODE"
ok "400 returned for invalid request"

echo ""
ok "ALL TESTS PASSED ✨"

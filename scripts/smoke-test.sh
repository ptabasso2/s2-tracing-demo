#!/usr/bin/env bash
# Exercises every endpoint (and every JDBC code path) once.
# Usage: ./scripts/smoke-test.sh [base_url]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
JSON='Content-Type: application/json'

step() { printf '\n=== %s\n' "$*"; }

step "Health"
curl -fsS "$BASE/actuator/health"; echo

step "SELECT  categories (PreparedStatement, no params)"
curl -fsS "$BASE/api/categories"; echo

step "SELECT  list with JOIN + filter (PreparedStatement)"
curl -fsS "$BASE/api/products?categoryId=2&limit=5"; echo

step "SELECT  count (plain java.sql.Statement)"
curl -fsS "$BASE/api/products/count"; echo

step "INSERT  single product"
CREATED=$(curl -fsS -X POST "$BASE/api/products" -H "$JSON" \
  -d '{"sku":"TST-001","name":"Smoke Test Gadget","categoryId":5,"price":9.99,"stock":3}')
echo "$CREATED"
ID=$(echo "$CREATED" | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "-> new id: $ID"

step "SELECT  by id"
curl -fsS "$BASE/api/products/$ID"; echo

step "UPDATE  product $ID"
curl -fsS -X PUT "$BASE/api/products/$ID" -H "$JSON" \
  -d '{"sku":"TST-001","name":"Smoke Test Gadget v2","categoryId":5,"price":12.50,"stock":7}'; echo

step "INSERT  batch (PreparedStatement.executeBatch)"
curl -fsS -X POST "$BASE/api/products/batch" -H "$JSON" -d '[
  {"sku":"BAT-001","name":"Batch item 1","categoryId":3,"price":10.00,"stock":1},
  {"sku":"BAT-002","name":"Batch item 2","categoryId":3,"price":20.00,"stock":2},
  {"sku":"BAT-003","name":"Batch item 3","categoryId":4,"price":30.00,"stock":3}
]'; echo

step "DELETE  product $ID"
curl -fsS -o /dev/null -w 'HTTP %{http_code}\n' -X DELETE "$BASE/api/products/$ID"

step "SELECT  deleted product (expect 404)"
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' "$BASE/api/products/$ID"

printf '\nDone.\n'

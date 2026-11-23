#!/usr/bin/env bash
set -euo pipefail

RUN_ID=$(date +%s)

echo "=== 1. Verifying Microservices Health ==="
for port in 8081 8082 8083; do
  status=$(curl -s "http://localhost:${port}/actuator/health" | jq -r '.status')
  echo "Port ${port}: ${status}"
done

echo -e "\n=== 2. Scenario A: Normal Transaction + Idempotency Replay ==="
IDEM_KEY="sim-normal-${RUN_ID}"
PAYLOAD_NORMAL='{"sourceAccountId":"ACC-1003","amount":420000.00,"currency":"IDR","txType":"PAYMENT","merchantName":"Kopi Tuku Kemang","merchantCategory":"FNB","countryCode":"IDN","city":"Surabaya","latitude":-7.2575,"longitude":112.7521,"deviceId":"DEV-C1","ipAddress":"103.30.3.31"}'
curl -s -X POST http://localhost:8081/api/v1/transactions -H "Content-Type: application/json" -H "Idempotency-Key: ${IDEM_KEY}" -d "${PAYLOAD_NORMAL}" | jq '{txId:.data.txId, replayed:.data.replayed, message:.message}'
curl -s -X POST http://localhost:8081/api/v1/transactions -H "Content-Type: application/json" -H "Idempotency-Key: ${IDEM_KEY}" -d "${PAYLOAD_NORMAL}" | jq '{txId:.data.txId, replayed:.data.replayed, message:.message}'

echo -e "\n=== 3. Scenario B: Velocity Spike Burst (4 Rapid Transactions on ACC-1004) ==="
for i in 1 2 3 4; do
  curl -s -X POST http://localhost:8081/api/v1/transactions \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: sim-burst-${RUN_ID}-${i}" \
    -d "{\"sourceAccountId\":\"ACC-1004\",\"amount\":300000.00,\"currency\":\"IDR\",\"txType\":\"PAYMENT\",\"merchantName\":\"Flash Digital Store #${i}\",\"merchantCategory\":\"DIGITAL\",\"countryCode\":\"IDN\",\"city\":\"Medan\",\"latitude\":3.5952,\"longitude\":98.6722,\"deviceId\":\"DEV-D1\",\"ipAddress\":\"103.40.4.41\"}" \
    | jq -c --argjson burst "${i}" '{burst: $burst, txId: .data.txId}'
done

echo -e "\n=== 4. Scenario C: Impossible Travel + Extreme Amount Anomaly (ACC-1003 to Tokyo) ==="
curl -s -X POST http://localhost:8081/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: sim-geo-${RUN_ID}" \
  -d '{"sourceAccountId":"ACC-1003","amount":12500000.00,"currency":"IDR","txType":"PAYMENT","merchantName":"Ginza Exclusive Boutique","merchantCategory":"LUXURY","countryCode":"JPN","city":"Tokyo","latitude":35.6762,"longitude":139.6503,"deviceId":"DEV-SPOOF","ipAddress":"45.155.205.88"}' \
  | jq '{txId:.data.txId, city:.data.city, amount:.data.amount}'

echo -e "\n=== 5. Scenario D: Blacklisted Account Transfer (ACC-1002 -> ACC-9999) ==="
curl -s -X POST http://localhost:8081/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: sim-bl-${RUN_ID}" \
  -d '{"sourceAccountId":"ACC-1002","targetAccountId":"ACC-9999","amount":1500000.00,"currency":"IDR","txType":"TRANSFER","merchantName":"P2P Wire","merchantCategory":"P2P","countryCode":"IDN","city":"Bandung","latitude":-6.9175,"longitude":107.6191,"deviceId":"DEV-B1","ipAddress":"103.20.2.21"}' \
  | jq '{txId:.data.txId, target:.data.targetAccountId}'

echo -e "\nWaiting 4 seconds for Kafka Streams scoring & Elasticsearch indexing..."
sleep 4

echo -e "\n=== 6. Kafka Streams Interactive Query (ACC-1004 Velocity Window) ==="
curl -s "http://localhost:8082/api/v1/stream/velocity/ACC-1004" | jq .

echo -e "\n=== 7. PostgreSQL Recursive CTE (Circular Transfer Rings) ==="
curl -s "http://localhost:8083/api/v1/investigations/sql/circular-transfers" | jq .

echo -e "\n=== 8. Elasticsearch Real-Time Fraud Analytics ==="
curl -s "http://localhost:8083/api/v1/investigations/elastic/analytics" | jq .
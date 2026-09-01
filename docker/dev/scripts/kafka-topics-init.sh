#!/usr/bin/env bash

set -euo pipefail

KAFKA_BROKER="${KAFKA_BROKER_INTERNAL:-kafka:19092}"

log_info(){
  echo -e "[INFO] $(date +'%Y-%m-%d %H:%M:%S') - $*"
}

log_error() {
    echo "[ERROR] $(date +'%Y-%m-%d %H:%M:%S') - $*" >&2
}

create_topic(){
  local topic_name=$1
  local partitions=$2
  local replication_factor=$3

  log_info "Creating topic: ${topic_name} (Partitions: ${partitions}, RF: ${replication_factor})"

  kafka-topics --bootstrap-server "${KAFKA_BROKER}" \
    --create \
    --if-not-exists \
    --topic "${topic_name}" \
    --partitions "${partitions}" \
    --replication-factor "${replication_factor}" \
    --config cleanup.policy=compact
}

main(){
  log_info "Kafka is ready. Creating topics..."

  create_topic "rag-cdc-topic" 3 1
  create_topic "rag-dlq-topic" 3 1
  create_topic "rag-extracting-topic" 3 1
  create_topic "rag-debezium-offset-topic" 1 1

  log_info "All topics create successfully"

  kafka-topics --bootstrap-server "${KAFKA_BROKER}" --list
}

main "$@"





#!/bin/sh
set -e # Stop the script if any command fails

echo 'MinIO is healthy! Configuring CLI'
mc alias set localminio http://minio:9000 minioadmin miniopassword

echo 'Creating bucket if not exists'
mc mb localminio/documents --ignore-existing

echo 'Enable bucket notification and wiring to RabbitMQ'
mc event add localminio/documents arn:minio:sqs::1:amqp --event put,delete

mc event list localminio/documents

echo 'MinIO init complete'
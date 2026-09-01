package com.hieulc.insightragworker.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Properties;

@ConfigurationProperties("debezium.outbox")
public record DebeziumProperties(
        String bootstrapServers,
        String offsetStorage,
        String offsetStorageTopic,
        String offsetStoragePartitions,
        String offsetStorageReplicationFactor,

        String databaseHostname,
        String databasePort,
        String databaseUser,
        String databasePassword,
        String databaseName,

        String maxQueueSize,
        String maxBatchSize
){
    public Properties asProperties(){
        Properties props = new Properties();

        props.setProperty("name", "outbox-engine");
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");

        props.setProperty("bootstrap.servers", bootstrapServers);
        props.setProperty("offset.storage", offsetStorage);
        props.setProperty("offset.storage.topic", offsetStorageTopic);
        props.setProperty("offset.storage.partitions", offsetStoragePartitions);
        props.setProperty("offset.storage.replication.factor", offsetStorageReplicationFactor);

        props.setProperty("database.hostname", databaseHostname);
        props.setProperty("database.port", databasePort);
        props.setProperty("database.user", databaseUser);
        props.setProperty("database.password", databasePassword);
        props.setProperty("database.dbname", databaseName);

        props.setProperty("max.queue.size", maxQueueSize);
        props.setProperty("max.batch.size", maxBatchSize);

        props.setProperty("publication.name", "dbz_publication");
        props.setProperty("slot.name", "debezium_replication_slot");
        props.setProperty("plugin.name", "pgoutput");
        props.setProperty("publication.autocreate.mode", "disabled");

        props.setProperty("snapshot.mode", "no_data");

        //prevent the db terminate the walsender due to replication timeout
        props.setProperty("heartbeat.interval.ms", "20000");
        props.setProperty("heartbeat.action.query", "SELECT pg_logical_emit_message(false, 'heartbeat', now()::varchar)");

        props.setProperty("topic.prefix", "rag-worker");
        props.setProperty("table.include.list", "worker_schema.document_outbox");

        return props;
    }

}

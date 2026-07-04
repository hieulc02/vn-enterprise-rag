package com.hieulc.insightragworker.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.util.Properties;

@ConfigurationProperties("debezium.outbox")
public record DebeziumProperties(
        String offsetStorage,

        String databaseHostname,
        String databasePort,
        String databaseUser,
        String databasePassword,
        String databaseName
){
    public Properties asProperties(){
        Properties props = new Properties();

        props.setProperty("name", "outbox-engine");
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.MemoryOffsetBackingStore");

//        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
//        props.setProperty("offset.storage.file.filename", offsetStorage);
//        ensureOffsetDirectoryExists();

        props.setProperty("offset.flush.interval.ms", "60000");

        props.setProperty("database.hostname", databaseHostname);
        props.setProperty("database.port", databasePort);
        props.setProperty("database.user", databaseUser);
        props.setProperty("database.password", databasePassword);
        props.setProperty("database.dbname", databaseName);

        props.setProperty("publication.name", "dbz_publication");
        props.setProperty("slot.name", "debezium_replication_slot");
        props.setProperty("plugin.name", "pgoutput");

        props.setProperty("publication.autocreate.mode", "disabled");

        props.setProperty("snapshot.mode", "no_data");

        props.setProperty("max.queue.size", "4");
        props.setProperty("max.batch.size", "2");

        //prevent the db terminate the walsender due to replication timeout
        props.setProperty("heartbeat.interval.ms", "10000");
        props.setProperty("heartbeat.action.query", "SELECT pg_logical_emit_message(false, 'heartbeat', now()::varchar)");

        props.setProperty("topic.prefix", "rag-worker");
        props.setProperty("table.include.list", "worker_schema.document_outbox");

        return props;
    }

    private void ensureOffsetDirectoryExists(){
        File file = new File(offsetStorage);
        File parentDir = file.getParentFile();
        if(parentDir != null && !parentDir.exists()) parentDir.mkdirs();
    }
}

package com.marklogic.batch.export.processor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.query.QueryManager;

@Component
@Configuration
@ConditionalOnProperty(name = "static.batch.export.kafka.enabled", havingValue="true")
public class KafkaExportProcessor {
	private final Logger logger = LoggerFactory.getLogger(KafkaExportProcessor.class);
	@Autowired
	private DatabaseClient dbClient;
	@Autowired
	private StandardEnvironment ENV;
	@Autowired
	protected QueryManager queryManager;
	@Autowired
	protected DataMovementManager dmvMgr;
	@Autowired
	protected JSONDocumentManager docMgr;

	final AtomicInteger failureBatchCount = new AtomicInteger();
	final AtomicLong urisProcessedCount = new AtomicLong();

	
	@Scheduled(cron = "${static.batch.file.export.schedule.cron}", zone = "${static.batch.file.export.schedule.zone}")
	public void processKafkaExport() {
		logger.warn("Implementation of exporting to a Kafka Topic.");
	}
}

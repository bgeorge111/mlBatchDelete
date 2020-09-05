package com.marklogic.batch.export.processor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marklogic.batch.config.BatchAMQPConfig;
import com.marklogic.batch.config.BatchConfig;
import com.marklogic.batch.config.BatchResultConfig;
import com.marklogic.batch.constants.AppConstants;
import com.marklogic.batch.lock.LockProvider;
import com.marklogic.batch.result.ResultObject;
import com.marklogic.batch.result.ResultProcessor;
import com.marklogic.batch.utils.GenUtils;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.ExportListener;
import com.marklogic.client.datamovement.JobTicket;
import com.marklogic.client.datamovement.ProgressListener;
import com.marklogic.client.datamovement.QueryBatcher;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonDatabindHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.RawCtsQueryDefinition;


@Component
@Configuration
@ConditionalOnProperty(name = "static.batch.export.amqp.enabled", havingValue="true")
public class AMQPExportProcessor {
	private final Logger logger = LoggerFactory.getLogger(AMQPExportProcessor.class);
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
    @Autowired
    private RabbitTemplate template;

    final AtomicInteger batchCount = new AtomicInteger();
	final AtomicLong urisProcessedCount = new AtomicLong();
	
	public BatchResultConfig initializeAMQPResultConfig() {
		BatchResultConfig resultConfig = new BatchResultConfig();
		logger.warn("Reloading AMQP Result Config");
		// Reload only dynamic properties
		resultConfig.setCollections(ENV.getProperty("result.export.amqp.collections").trim().split("\\s*,\\s*"));
		resultConfig.setPermissions(ENV.getProperty("result.export.amqp.permissions").trim().split("\\s*,\\s*"));
		resultConfig.setLocation(ENV.getProperty("result.export.amqp.file.location"));
		resultConfig.setResulttype(ENV.getProperty("result.export.amqp.type"));
		resultConfig.setPrefix(ENV.getProperty("result.export.amqp.uri.prefix"));
		return resultConfig;
	}

	public BatchConfig initializeAMQPConfig(BatchResultConfig resultConfig) {
		BatchConfig config = new BatchConfig();
		BatchAMQPConfig amqpConfig = new BatchAMQPConfig();
		logger.warn("Reloading AMQP Config");
		amqpConfig.setExchange(ENV.getProperty("static.batch.export.amqp.exchange"));
		amqpConfig.setRoutingKey(ENV.getProperty("static.batch.export.amqp.routingKey"));
		amqpConfig.setMsgAppId(ENV.getProperty("static.batch.export.amqp.appId"));
		// Reload only dynamic properties
		config.setBatchSize(Integer.parseInt(ENV.getProperty("dynamic.batch.export.amqp.batchSize").trim()));
		config.setThreads(Integer.parseInt(ENV.getProperty("dynamic.batch.export.amqp.threads").trim()));
		config.setSerialized(ENV.getProperty("dynamic.batch.export.amqp.query.serialized").trim());
		String query = ENV.getProperty("dynamic.batch.export.amqp.query");
		config.setQuery(query);
		config.setAmqpConfig(amqpConfig);
		config.setResultConfig(resultConfig);
		config.setNodeName(ENV.getProperty("spring.instance.id").trim());
		return config;
	}
	
	public void createBatchResultDocument(ResultObject ro, BatchConfig config) {
		DocumentMetadataHandle metadata = new DocumentMetadataHandle();
		GenUtils utils = new GenUtils();
		JacksonDatabindHandle<ResultObject> handle = new JacksonDatabindHandle<ResultObject>(ro);
		if ("DATABASE".equalsIgnoreCase(config.getResultConfig().getResulttype())
				|| "BOTH".equalsIgnoreCase(config.getResultConfig().getResulttype())) {
			DocumentPermissions permissions = metadata.getPermissions();
			metadata.getCollections().addAll(config.getResultConfig().getCollections());
			utils.parsePermissions(config.getResultConfig().getPermissions(), permissions);
			metadata.setPermissions(permissions);
			docMgr.write(ro.getBatchResultUri(), metadata, handle);
		}
		if ("FILE".equalsIgnoreCase(config.getResultConfig().getResulttype())
				|| "BOTH".equalsIgnoreCase(config.getResultConfig().getResulttype())) {
			FileWriter fileWriter = null;
			try {
				File file = new File(
						config.getResultConfig().getLocation() + ro.getBatchResultUri().replaceAll("/", "_"));
				logger.info("Writing Result File ==> " + file.getAbsolutePath());
				fileWriter = new FileWriter(file);
				fileWriter.write(handle.toString());
			} catch (IOException e) {
				logger.error("IOException when writing to Result File.." + e.getMessage());
			} finally {
				try {
					if (fileWriter != null) {
						fileWriter.flush();
						fileWriter.close();
					}
				} catch (IOException e) {
					logger.error("IOException when writing to Result File.." + e.getMessage());
				}
			}
		}

	}
	
	@Scheduled(cron = "${static.batch.amqp.export.schedule.cron}", zone = "${static.batch.amqp.export.schedule.zone}")
	public void processAMQPExport() {
		String pattern = "yyyy-MM-dd HH:mm:ss.SSSZ";
		String serializedQuery = "";
		LockProvider lockProvider = new LockProvider(); 
		ResultProcessor resultProcessor = new ResultProcessor();
		Date startTs = new Date();
		BatchResultConfig resultConfig = initializeAMQPResultConfig();
		BatchConfig config = initializeAMQPConfig(resultConfig);
		ResultObject ro = new ResultObject();
		MessageProperties messageProperties = new MessageProperties();
		messageProperties.setAppId(config.getAmqpConfig().getMsgAppId());
		template.setExchange(config.getAmqpConfig().getExchange());
		template.setRoutingKey(config.getAmqpConfig().getRoutingKey());
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		ro.setRunTs(sdf.format(new Date()));
		if (lockProvider.lockService(dbClient, "amqp-export",config.getNodeName()) != 0 )
		{
			logger.info("The service is locked by another process. Exiting");
			return;
		}
		logger.warn("AMQP Export Query::" + config.getQuery());
		if ("FALSE".equalsIgnoreCase(config.getSerialized())) {
			serializedQuery = dbClient.newServerEval().javascript(config.getQuery() + ".toObject()")
					.evalAs(String.class);
		} else {
			serializedQuery = "{'ctsquery':" + config.getQuery() + "}";
		}
		logger.warn("Serialized Query::" + serializedQuery);
		StringHandle handle = new StringHandle("{'ctsquery':" + serializedQuery + "}").withFormat(Format.JSON);

		RawCtsQueryDefinition query = queryManager.newRawCtsQueryDefinition(handle);
		final AtomicLong urisProcessedCount = new AtomicLong();
		final QueryBatcher batcher = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize()).withThreadCount(config.getThreads())
				.onUrisReady(new ExportListener().onDocumentReady(doc -> {
					batchCount.incrementAndGet();
					try {
						this.template.send(new Message(doc.getContent(new StringHandle()).toString().getBytes(),messageProperties));
					} catch (final Exception e) {
						throw new RuntimeException(e);
					}
				})).onUrisReady(new ProgressListener().onProgressUpdate(
						progressUpdate -> { 
							logger.info(progressUpdate.getProgressAsString());
				})).onUrisReady(batch -> {
							urisProcessedCount.getAndAdd(Long.valueOf(batch.getItems().length));
				}).withJobName("AMQP Export Data Movement Job")
				.onQueryFailure(exception -> {logger.error("Query Error in AMQP Export job." + exception.getMessage());})
				.withConsistentSnapshot(); 
		final JobTicket ticket = dmvMgr.startJob(batcher);
		logger.warn("Job id-->" + ticket.getJobId() + "AMQP Export Data Movement Job Started");
		batcher.awaitCompletion();
		ro.setBatchJobDescription(batcher.getJobName());
		ro.setBatchJobName(batcher.getJobName());
		ro.setBatchJobType(ticket.getJobType().name());
		ro.setBatchResultCount(urisProcessedCount.longValue());
		ro.setBatchResultUri(config.getResultConfig().getPrefix() + ticket.getJobId() + ".json");
		ro.setBatchJobId(ticket.getJobId());
		ro.setBatchJobStartTs(sdf.format(batcher.getJobStartTime().getTime()));
		ro.setBatchJobEndTs(sdf.format(batcher.getJobEndTime().getTime()));
		ro.setBatchDuration(
				(batcher.getJobEndTime().getTime().getTime() - batcher.getJobStartTime().getTime().getTime()) / 1000);
		ro.setQuery(serializedQuery);
		ro.setResult(AppConstants.SUCCESS);
		ro.setMessage("Batch Job Successfully completed.");
		ro.setRunBy(config.getNodeName());
		dmvMgr.stopJob(ticket);

		Date endTs = new Date();
		createBatchResultDocument(ro, config);
		lockProvider.unLockService(dbClient, "amqp-export",config.getNodeName());
		logger.warn("AMQP Export Data Movement Job --> " + ticket.getJobId() + " Ended in "
				+ (endTs.getTime() - startTs.getTime()) / 1000 + " seconds");
		logger.warn("Job id-->" + ticket.getJobId() + ". AMQP Export Data Movement Job Failed Batch Count:"
				+ batchCount.get());
	}
}

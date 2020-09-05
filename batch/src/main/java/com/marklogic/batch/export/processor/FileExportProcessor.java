package com.marklogic.batch.export.processor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
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

import com.marklogic.batch.config.BatchConfig;
import com.marklogic.batch.config.BatchFileExportConfig;
import com.marklogic.batch.config.BatchResultConfig;
import com.marklogic.batch.constants.AppConstants;
import com.marklogic.batch.lock.LockProvider;
import com.marklogic.batch.result.ResultObject;
import com.marklogic.batch.result.ResultProcessor;
import com.marklogic.batch.utils.GenUtils;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.ExportToWriterListener;
import com.marklogic.client.datamovement.JobTicket;
import com.marklogic.client.datamovement.ProgressListener;
import com.marklogic.client.datamovement.QueryBatcher;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.document.ServerTransform;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonDatabindHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.RawCtsQueryDefinition;

@Component
@Configuration
@ConditionalOnProperty(name = "static.batch.export.file.enabled", havingValue = "true")
public class FileExportProcessor {
	private final Logger logger = LoggerFactory.getLogger(FileExportProcessor.class);
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

	final AtomicInteger batchCount = new AtomicInteger();
	final AtomicLong urisProcessedCount = new AtomicLong();

	public BatchResultConfig initializeFileResultConfig() {
		BatchResultConfig resultConfig = new BatchResultConfig();
		logger.warn("Reloading File Result Config");
		// Reload only dynamic properties
		resultConfig.setCollections(ENV.getProperty("result.export.file.collections").trim().split("\\s*,\\s*"));
		resultConfig.setPermissions(ENV.getProperty("result.export.file.permissions").trim().split("\\s*,\\s*"));
		resultConfig.setLocation(ENV.getProperty("result.export.file.file.location"));
		resultConfig.setResulttype(ENV.getProperty("result.export.file.type"));
		resultConfig.setPrefix(ENV.getProperty("result.export.file.uri.prefix"));
		return resultConfig;
	}

	public BatchConfig initializeFileConfig(BatchResultConfig resultConfig) {
		BatchConfig config = new BatchConfig();
		BatchFileExportConfig fileConfig = new BatchFileExportConfig();
		logger.warn("Reloading File Config");
		fileConfig.setPath(ENV.getProperty("dynamic.batch.export.file.path").trim());
		fileConfig.setTransform(ENV.getProperty("dynamic.batch.export.file.transform").trim());
		config.setBatchSize(Integer.parseInt(ENV.getProperty("dynamic.batch.export.file.batchSize").trim()));
		config.setThreads(Integer.parseInt(ENV.getProperty("dynamic.batch.export.file.threads").trim()));
		config.setSerialized(ENV.getProperty("dynamic.batch.export.file.query.serialized").trim());
		String query = ENV.getProperty("dynamic.batch.export.file.query");
		config.setQuery(query);
		config.setFileConfig(fileConfig);
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

	@Scheduled(cron = "${static.batch.export.file.schedule.cron}", zone = "${static.batch.export.file.schedule.zone}")
	public void processFileExport() throws IOException {
		String pattern = "yyyy-MM-dd HH:mm:ss.SSSZ";
		String serializedQuery = "";
		ResultProcessor resultProcessor = new ResultProcessor();
		LockProvider lockProvider = new LockProvider();
		Date startTs = new Date();
		BatchResultConfig resultConfig = initializeFileResultConfig();
		BatchConfig config = initializeFileConfig(resultConfig);
		ResultObject ro = new ResultObject();
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		ro.setRunTs(sdf.format(new Date()));
		if (lockProvider.lockService(dbClient, "file-export", config.getNodeName()) != 0) {
			logger.info("The service is locked by another process. Exiting");
			return;
		}
		logger.warn("File Export Query::" + config.getQuery());
		if ("FALSE".equalsIgnoreCase(config.getSerialized())) {
			serializedQuery = dbClient.newServerEval().javascript(config.getQuery() + ".toObject()")
					.evalAs(String.class);
		} else {
			serializedQuery = "{'ctsquery':" + config.getQuery() + "}";
		}
		logger.warn("Serialized Query::" + serializedQuery);
		StringHandle handle = new StringHandle("{'ctsquery':" + serializedQuery + "}").withFormat(Format.JSON);

		RawCtsQueryDefinition query = queryManager.newRawCtsQueryDefinition(handle);
		FileWriter fileWriter = null;
		try {
			fileWriter = new FileWriter(config.getFileConfig().getPath() + UUID.randomUUID() + ".out");
		} catch (IOException e) {
			logger.error("Unable to Create Output File..==>" + e.getMessage());
			String uuid = UUID.randomUUID().toString();
			ro.setBatchJobName("NA");
			ro.setBatchJobType("NA");
			ro.setBatchResultCount(0L);
			ro.setBatchResultUri(config.getResultConfig().getPrefix() + "/error/" + uuid + ".json");
			ro.setBatchJobId("NA");
			ro.setBatchJobStartTs(null);
			ro.setBatchJobEndTs(null);
			ro.setBatchDuration(0L);
			ro.setQuery(serializedQuery);
			ro.setResult(AppConstants.FAILED);
			ro.setMessage(e.getMessage());
			createBatchResultDocument(ro, config);
			throw new RuntimeException(e);
		}
		QueryBatcher batcher = getQueryBatcher(config, query, fileWriter);

		final JobTicket ticket = dmvMgr.startJob(batcher);
		logger.warn("Job id-->" + ticket.getJobId() + "File Export Data Movement Job Started");
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
		fileWriter.close();
		urisProcessedCount.set(0L);
		logger.info("File Export Data Movement Job Service Unlocked");
		lockProvider.unLockService(dbClient, "file-export", config.getNodeName());
		logger.warn("File Export Data Movement Job --> " + ticket.getJobId() + " Ended in "
				+ (endTs.getTime() - startTs.getTime()) / 1000 + " seconds");
		logger.warn("Job id-->" + ticket.getJobId() + ". File Export Data Movement Job Failed Batch Count:"
				+ batchCount.get());
	}

	private QueryBatcher getQueryBatcher(BatchConfig config, RawCtsQueryDefinition query, FileWriter fileWriter) {
		ServerTransform myTransform = new ServerTransform(config.getFileConfig().getTransform());
		QueryBatcher batcher = null;
		if (myTransform.isEmpty()) {
			batcher = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize())
					.withThreadCount(config.getThreads()).onUrisReady(
							new ExportToWriterListener(fileWriter).withRecordSuffix("\n").onGenerateOutput(record -> {
								return record.getContent(new StringHandle()).toString();
							}))
					.onUrisReady(new ProgressListener().onProgressUpdate(progressUpdate -> {
						logger.info(progressUpdate.getProgressAsString());
					})).withJobName("File Export Data Movement Job")
					.onQueryFailure(exception -> exception.printStackTrace()).withConsistentSnapshot();
		} else {
			batcher = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize())
					.withThreadCount(config.getThreads()).onUrisReady(new ExportToWriterListener(fileWriter)
							.withTransform(myTransform).withRecordSuffix("\n").onGenerateOutput(record -> {
								return record.getContent(new StringHandle()).toString();
							}))
					.onUrisReady(batch -> {
						urisProcessedCount.getAndAdd(Long.valueOf(batch.getItems().length));
			        })
					.onUrisReady(new ProgressListener().onProgressUpdate(progressUpdate -> {
						logger.info(progressUpdate.getProgressAsString());
					})).withJobName("File Export Data Movement Job")
					.onQueryFailure(exception -> exception.printStackTrace()).withConsistentSnapshot();
		}
		return batcher;

	}
}

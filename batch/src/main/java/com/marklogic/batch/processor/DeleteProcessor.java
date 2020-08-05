package com.marklogic.batch.processor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marklogic.batch.config.DeleteConfig;
import com.marklogic.batch.config.DeleteResultConfig;
import com.marklogic.batch.result.ResultObject;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.DeleteListener;
import com.marklogic.client.datamovement.JobTicket;
import com.marklogic.client.datamovement.ProgressListener;
import com.marklogic.client.datamovement.QueryBatcher;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonDatabindHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.RawCtsQueryDefinition;
import com.marklogic.utils.GenUtils;

@Component
public class DeleteProcessor {
	private final Logger logger = LoggerFactory.getLogger(DeleteProcessor.class);
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

	public DeleteResultConfig initializeDeleteResultConfig() {
		DeleteResultConfig resultConfig = new DeleteResultConfig();
		logger.warn("Reloading Delete Result Config");
		// Reload only dynamic properties
		resultConfig.setCollections(ENV.getProperty("result.delete.collections").trim().split("\\s*,\\s*"));
		resultConfig.setPermissions(ENV.getProperty("result.delete.permissions").trim().split("\\s*,\\s*"));
		resultConfig.setLocation(ENV.getProperty("result.delete.file.location"));
		resultConfig.setResulttype(ENV.getProperty("result.delete.type"));
		resultConfig.setPrefix(ENV.getProperty("result.delete.uri.prefix"));
		return resultConfig;
	}

	public DeleteConfig initializeDeleteConfig(DeleteResultConfig resultConfig) {
		DeleteConfig config = new DeleteConfig();
		logger.warn("Reloading Delete Config");
		// Reload only dynamic properties
		config.setCollections(ENV.getProperty("dynamic.batch.delete.collections").trim().split("\\s*,\\s*"));
		config.setBatchSize(Integer.parseInt(ENV.getProperty("dynamic.batch.delete.batchSize").trim()));
		config.setThreads(Integer.parseInt(ENV.getProperty("dynamic.batch.delete.threads").trim()));
		String query = ENV.getProperty("dynamic.batch.delete.query");
		config.setQuery(query);
		// config.setQuery("{'ctsquery':" + query + "}");
		config.setResultConfig(resultConfig);
		return config;
	}

	public void createBatchResultDocument(ResultObject ro, DeleteConfig config) {
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
				File file = new File(config.getResultConfig().getLocation()+ro.getBatchResultUri().replaceAll("/", "_"));
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

	@Scheduled(cron = "${static.batch.delete.schedule.cron}", zone = "${static.batch.delete.schedule.zone}")
	public void processBatchDelete() {
		String pattern = "yyyy-MM-dd HH:mm:ss.SSSZ";
		Date startTs = new Date();
		DeleteResultConfig resultConfig = initializeDeleteResultConfig();
		DeleteConfig config = initializeDeleteConfig(resultConfig);
		logger.warn("Batch Delete Query::" + config.getQuery());
		String serializedQuery = dbClient.newServerEval().javascript(config.getQuery() + ".toObject()")
				.evalAs(String.class);
		logger.warn("Serialized Query::" + serializedQuery);
		StringHandle handle = new StringHandle("{'ctsquery':" + serializedQuery + "}").withFormat(Format.JSON);

		RawCtsQueryDefinition query = queryManager.newRawCtsQueryDefinition(handle);
		query.setCollections(config.getCollections());
		ResultObject ro = new ResultObject();

		final AtomicInteger failureBatchCount = new AtomicInteger();
		final AtomicLong urisProcessedCount = new AtomicLong();
		final QueryBatcher batcher = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize())
				.withThreadCount(config.getThreads())
				.onUrisReady(new DeleteListener().onFailure((batch2, throwable) -> {
					failureBatchCount.incrementAndGet();
					logger.error("Delete Job Error." + throwable.getLocalizedMessage());
				})).onUrisReady(new ProgressListener().onProgressUpdate(progressUpdate -> {
					// urisProcessedCount.getAndAdd(progressUpdate.getTotalResults());
					logger.warn(progressUpdate.getProgressAsString());
				})).onUrisReady(batch -> {
					urisProcessedCount.getAndAdd(Long.valueOf(batch.getItems().length));
				}).withJobName("Delete Job").onQueryFailure(exception -> exception.printStackTrace())
				.onJobCompletion(batch -> {
				}).withConsistentSnapshot();
		final JobTicket ticket = dmvMgr.startJob(batcher);
		logger.warn("Job id-->" + ticket.getJobId() + "Delete Data Movement Job Started");
		batcher.awaitCompletion();
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
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
		dmvMgr.stopJob(ticket);

		Date endTs = new Date();
		createBatchResultDocument(ro, config);
		logger.warn("Delete Data Movement Job --> " + ticket.getJobId() + " Ended in "
				+ (endTs.getTime() - startTs.getTime()) / 1000 + " seconds");
		logger.warn("Job id-->" + ticket.getJobId() + ". Delete Data Movement Job Failed Batch Count:"
				+ failureBatchCount.get());
	}
}

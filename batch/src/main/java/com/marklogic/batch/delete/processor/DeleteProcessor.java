package com.marklogic.batch.delete.processor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
import com.marklogic.batch.config.BatchResultConfig;
import com.marklogic.batch.result.ResultObject;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.DeleteListener;
import com.marklogic.client.datamovement.JobTicket;
import com.marklogic.client.datamovement.ProgressListener;
import com.marklogic.client.datamovement.QueryBatcher;
import com.marklogic.client.datamovement.UrisToWriterListener;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonDatabindHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.RawCtsQueryDefinition;
import com.marklogic.batch.constants.AppConstants;
import com.marklogic.batch.lock.LockProvider;
import com.marklogic.batch.utils.GenUtils;

@Component
@Configuration
@ConditionalOnProperty(name = "static.batch.delete.enabled", havingValue="true")
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
	
	public BatchResultConfig initializeDeleteResultConfig() {
		BatchResultConfig resultConfig = new BatchResultConfig();
		logger.warn("Reloading Delete Result Config");
		// Reload only dynamic properties
		resultConfig.setCollections(ENV.getProperty("result.delete.collections").trim().split("\\s*,\\s*"));
		resultConfig.setPermissions(ENV.getProperty("result.delete.permissions").trim().split("\\s*,\\s*"));
		resultConfig.setLocation(ENV.getProperty("result.delete.file.location"));
		resultConfig.setResulttype(ENV.getProperty("result.delete.type"));
		resultConfig.setPrefix(ENV.getProperty("result.delete.uri.prefix"));
		return resultConfig;
	}

	public BatchConfig initializeDeleteConfig(BatchResultConfig resultConfig) {
		BatchConfig config = new BatchConfig();
		logger.warn("Reloading Delete Config");
		// Reload only dynamic properties
		config.setBatchSize(Integer.parseInt(ENV.getProperty("dynamic.batch.delete.batchSize").trim()));
		config.setThreads(Integer.parseInt(ENV.getProperty("dynamic.batch.delete.threads").trim()));
		config.setSerialized(ENV.getProperty("dynamic.batch.delete.query.serialized").trim());
		config.setConsistentSnapshotFlag(ENV.getProperty("dynamic.batch.delete.consistenSnapshot").trim());
		config.setQueuedURIsFlag(ENV.getProperty("dynamic.batch.delete.queuedURIs").trim());
		String query = ENV.getProperty("dynamic.batch.delete.query");
		config.setQuery(query);
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

	private QueryBatcher getQueryBatcherWithConsistentSnapshot(BatchConfig config, RawCtsQueryDefinition query) {
		QueryBatcher batcher = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize())
				.withThreadCount(config.getThreads())
				.onUrisReady(new DeleteListener().onFailure((batch2, throwable) -> {
					failureBatchCount.incrementAndGet();
					logger.error("Delete Job Error." + throwable.getLocalizedMessage());
				})).onUrisReady(new ProgressListener().onProgressUpdate(progressUpdate -> {
					logger.info(progressUpdate.getProgressAsString());
				})).onUrisReady(batch -> {
					urisProcessedCount.getAndAdd(Long.valueOf(batch.getItems().length));
				}).withJobName("Delete Job with Consistent SnapShot").onQueryFailure(exception -> {
					logger.error("Query Error in Delete Job. " + exception.getMessage());
				}).onJobCompletion(batch -> {
				}).withConsistentSnapshot();
		return batcher;
	}

	private QueryBatcher getQueryBatcherWithSerializedURIs(BatchConfig config, RawCtsQueryDefinition query)
			throws IOException {
		String uriCacheFileName = "uriCache_" + new Date().getTime() + ".txt";
		File file = new File(config.getResultConfig().getLocation()+uriCacheFileName);
		FileWriter writer = new FileWriter(file);
		QueryBatcher getUris = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize())
				.withThreadCount(config.getThreads())
				.onUrisReady(new UrisToWriterListener(writer)).onQueryFailure(exception -> {
					logger.error("Query Error" + exception.getMessage());
				});
		JobTicket getUrisTicket = dmvMgr.startJob(getUris);
		getUris.awaitCompletion();
		dmvMgr.stopJob(getUrisTicket);
		writer.flush();
		writer.close();
		BufferedReader reader = new BufferedReader(new FileReader(file));
		QueryBatcher batcher = dmvMgr.newQueryBatcher(reader.lines().iterator()).withBatchSize(config.getBatchSize())
				.withThreadCount(config.getThreads())
				.onUrisReady(new DeleteListener().onFailure((batch2, throwable) -> {
					failureBatchCount.incrementAndGet();
					logger.error("Delete Job Error." + throwable.getLocalizedMessage());
				})).onUrisReady(new ProgressListener().onProgressUpdate(progressUpdate -> {
					logger.info(progressUpdate.getProgressAsString());
				})).onUrisReady(batch -> {
					urisProcessedCount.getAndAdd(Long.valueOf(batch.getItems().length));
				}).withJobName("Delete Job with Serialized URIs").onQueryFailure(exception -> {
					logger.error("Query Error in Delete Job. " + exception.getMessage());
				}).onJobCompletion(batch -> {
				});
		return batcher;
	}

	private QueryBatcher getQueryBatcherWithQueuedURIs(BatchConfig config, RawCtsQueryDefinition query)
			throws IOException {
		List<String> list = new ArrayList<String>();
		List<String> uris = Collections.synchronizedList(list);
		QueryBatcher getUris = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize())
				.onUrisReady(batch -> uris.addAll(Arrays.asList(batch.getItems())))
				.withThreadCount(config.getThreads())
				.onQueryFailure(exception -> exception.printStackTrace());
		JobTicket getUrisTicket = dmvMgr.startJob(getUris);
		getUris.awaitCompletion();
		dmvMgr.stopJob(getUrisTicket);

		QueryBatcher batcher = dmvMgr.newQueryBatcher(uris.iterator()).withThreadCount(config.getThreads()).withBatchSize(config.getBatchSize())
				.onUrisReady(new DeleteListener().onFailure((batch2, throwable) -> {
					failureBatchCount.incrementAndGet();
					logger.error("Delete Job Error." + throwable.getLocalizedMessage());
				})).onUrisReady(new ProgressListener().onProgressUpdate(progressUpdate -> {
					logger.info(progressUpdate.getProgressAsString());
				})).onUrisReady(batch -> {
					urisProcessedCount.getAndAdd(Long.valueOf(batch.getItems().length));
				}).withJobName("Delete Job with Queued URIs").onQueryFailure(exception -> {
					logger.error("Query Error in Delete Job. " + exception.getMessage());
				}).onJobCompletion(batch -> {
				});
		return batcher;
	}

	@Scheduled(cron = "${static.batch.delete.schedule.cron}", zone = "${static.batch.delete.schedule.zone}")
	public void processBatchDelete() throws IOException {
		String pattern = "yyyy-MM-dd HH:mm:ss.SSSZ";
		String serializedQuery = "";
		Date startTs = new Date();
		LockProvider lockProvider = new LockProvider(); 
		BatchResultConfig resultConfig = initializeDeleteResultConfig();
		BatchConfig config = initializeDeleteConfig(resultConfig);
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		ResultObject ro = new ResultObject();
		ro.setRunTs(sdf.format(new Date()));
		if (lockProvider.lockService(dbClient, "delete",config.getNodeName()) != 0 )
		{
			logger.info("The service is locked by another process. Exiting");
			return;
		}
		logger.warn("Batch Delete Query::" + config.getQuery());
		if ("FALSE".equalsIgnoreCase(config.getSerialized())) {
			serializedQuery = dbClient.newServerEval().javascript(config.getQuery() + ".toObject()")
					.evalAs(String.class);
		} else {
			serializedQuery = "{'ctsquery':" + config.getQuery() + "}";
		}
		logger.warn("Serialized Query::" + serializedQuery);
		StringHandle handle = new StringHandle("{'ctsquery':" + serializedQuery + "}").withFormat(Format.JSON);

		RawCtsQueryDefinition query = queryManager.newRawCtsQueryDefinition(handle);

		/*
		 * Making a determination between using ConsistentSnapshot or not. If not using
		 * consistent snapshot the URIs are first fetched with a disk write and then
		 * deleting.
		 */
		QueryBatcher batcher = null;
		if ("TRUE".compareToIgnoreCase(config.getConsistentSnapshotFlag()) == 0 ) {
			batcher = getQueryBatcherWithConsistentSnapshot(config, query);
		}
		else {
			if ("TRUE".compareToIgnoreCase(config.getQueuedURIsFlag()) == 0 ) {
				batcher = getQueryBatcherWithQueuedURIs(config, query);
			}
			else {
				batcher = getQueryBatcherWithSerializedURIs(config,query);
			}
		}
		final JobTicket ticket = dmvMgr.startJob(batcher);
		logger.warn("Job id-->" + ticket.getJobId() + "Delete Data Movement Job Started");
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
		urisProcessedCount.set(0L);
		dmvMgr.stopJob(ticket);

		Date endTs = new Date();
		createBatchResultDocument(ro, config);
		lockProvider.unLockService(dbClient, "delete",config.getNodeName());
		logger.warn("Delete Data Movement Job --> " + ticket.getJobId() + " Ended in "
				+ (endTs.getTime() - startTs.getTime()) / 1000 + " seconds");
		logger.warn("Job id-->" + ticket.getJobId() + ". Delete Data Movement Job Failed Batch Count:"
				+ failureBatchCount.get());
	}
}

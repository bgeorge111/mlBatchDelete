package com.marklogic.batch.export.processor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
import com.marklogic.batch.config.BatchRDBMSConfig;
import com.marklogic.batch.config.BatchResultConfig;
import com.marklogic.batch.result.ResultObject;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.datamovement.ExportListener;
import com.marklogic.client.datamovement.JobTicket;
import com.marklogic.client.datamovement.ProgressListener;
import com.marklogic.client.datamovement.QueryBatcher;
import com.marklogic.client.document.DocumentRecord;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.document.ServerTransform;
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
@ConditionalOnProperty(name = "static.batch.export.rdbms.enabled", havingValue="true")
public class SQLServerExportProcessor {
	private final Logger logger = LoggerFactory.getLogger(SQLServerExportProcessor.class);
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
	Connection connection;
	
	public BatchResultConfig initializeRDBMSResultConfig() {
		BatchResultConfig resultConfig = new BatchResultConfig();
		logger.warn("Reloading RDBMS Result Config");
		// Reload only dynamic properties
		resultConfig.setCollections(ENV.getProperty("result.export.rdbms.collections").trim().split("\\s*,\\s*"));
		resultConfig.setPermissions(ENV.getProperty("result.export.rdbms.permissions").trim().split("\\s*,\\s*"));
		resultConfig.setLocation(ENV.getProperty("result.export.rdbms.file.location"));
		resultConfig.setResulttype(ENV.getProperty("result.export.rdbms.type"));
		resultConfig.setPrefix(ENV.getProperty("result.export.rdbms.uri.prefix"));
		return resultConfig;
	}

	public BatchConfig initializeRDBMSConfig(BatchResultConfig resultConfig) {
		BatchConfig config = new BatchConfig();
		BatchRDBMSConfig rdbmsConfig = new BatchRDBMSConfig();
		logger.warn("Reloading RDBMS Config");
		rdbmsConfig.setConnectionUrl(ENV.getProperty("static.batch.export.rdbms.connectionUrl"));
		rdbmsConfig.setSql(ENV.getProperty("static.batch.export.rdbms.sql"));
		rdbmsConfig.setCsvTransform(ENV.getProperty("static.batch.export.rdbms.transform"));
		// Reload only dynamic properties
		config.setBatchSize(Integer.parseInt(ENV.getProperty("dynamic.batch.export.rdbms.batchSize").trim()));
		config.setThreads(Integer.parseInt(ENV.getProperty("dynamic.batch.export.rdbms.threads").trim()));
		config.setSerialized(ENV.getProperty("dynamic.batch.export.rdbms.query.serialized").trim());
		String query = ENV.getProperty("dynamic.batch.export.rdbms.query");
		config.setQuery(query);
		config.setRdbmsConfig(rdbmsConfig);
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

	public long performDBInsert(DocumentRecord doc, String insertsql) throws SQLException {
		String[] csvParts = doc.getContent(new StringHandle()).toString().split(",+");
		PreparedStatement ps = connection.prepareStatement(insertsql);
		for (int i=0 ; i< csvParts.length; i++) {
			ps.setString((i+1), csvParts[i]);
		}
		ps.executeUpdate();
		ps.close();
		return 0L;
	}

	@Scheduled(cron = "${static.batch.export.rdbms.schedule.cron}", zone = "${static.batch.export.rdbms.schedule.zone}")
	public void processRDBMSExport() throws SQLException {
		String pattern = "yyyy-MM-dd HH:mm:ss.SSSZ";
		String serializedQuery = "";
		Date startTs = new Date();
		String connectionUrl = "";
		String insertSql;
		BatchResultConfig resultConfig = initializeRDBMSResultConfig();
		LockProvider lockProvider = new LockProvider(); 
		BatchConfig config = initializeRDBMSConfig(resultConfig);
		ResultObject ro = new ResultObject();
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		ro.setRunTs(sdf.format(new Date()));
		insertSql = config.getRdbmsConfig().getSql();
		connectionUrl = config.getRdbmsConfig().getConnectionUrl();
		final AtomicLong urisProcessedCount = new AtomicLong();
		if (lockProvider.lockService(dbClient, "rdbms-export",config.getNodeName()) != 0 )
		{
			logger.info("The service is locked by another process. Exiting");
			return;
		}
		logger.warn("RDBMS Export Query::" + config.getQuery());
		if ("FALSE".equalsIgnoreCase(config.getSerialized())) {
			serializedQuery = dbClient.newServerEval().javascript(config.getQuery() + ".toObject()")
					.evalAs(String.class);
		} else {
			serializedQuery = "{'ctsquery':" + config.getQuery() + "}";
		}
		logger.warn("Serialized Query::" + serializedQuery);
		StringHandle handle = new StringHandle("{'ctsquery':" + serializedQuery + "}").withFormat(Format.JSON);

		RawCtsQueryDefinition query = queryManager.newRawCtsQueryDefinition(handle);
		logger.warn("Connecting to SQL Server ... ");
		try {
			connection = DriverManager.getConnection(connectionUrl);
		} catch (Exception e) {
			logger.error("Unable to Create RDBMS Connection Manager..==>" + e.getMessage());
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
		
		ServerTransform csvTransform = new ServerTransform(config.getRdbmsConfig().getCsvTransform());
		final QueryBatcher batcher = dmvMgr.newQueryBatcher(query).withBatchSize(config.getBatchSize()).withThreadCount(config.getThreads())
				.onUrisReady(new ExportListener().withTransform(csvTransform).onDocumentReady(doc -> {
					batchCount.incrementAndGet();
					try {
						performDBInsert(doc, insertSql);
					} catch (final Exception e) {
						throw new RuntimeException(e);
					}
				})).onUrisReady(new ProgressListener().onProgressUpdate(
						progressUpdate -> { 
							logger.info(progressUpdate.getProgressAsString());
				})).onUrisReady(batch -> {
							urisProcessedCount.getAndAdd(Long.valueOf(batch.getItems().length));
				}).withJobName(" RDBMS Export Data Movement Job")
				.onQueryFailure(exception -> {logger.error("Query Error in Exprt RDBMS job." + exception.getMessage());})
				.withConsistentSnapshot(); 
		final JobTicket ticket = dmvMgr.startJob(batcher);
		logger.warn("Job id-->" + ticket.getJobId() + "RDBMS Export Data Movement Job Started");
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
		lockProvider.unLockService(dbClient, "rdbms-export",config.getNodeName());
		connection.close();
		logger.warn("RDBMS Export Data Movement Job --> " + ticket.getJobId() + " Ended in "
				+ (endTs.getTime() - startTs.getTime()) / 1000 + " seconds");
		logger.warn("Job id-->" + ticket.getJobId() + ". RDBMS Export Data Movement Job Failed Batch Count:"
				+ batchCount.get());
	}
}

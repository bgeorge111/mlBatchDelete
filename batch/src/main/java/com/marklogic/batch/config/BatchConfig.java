package com.marklogic.batch.config;

public class BatchConfig {
	String serialized;
	String query;
	Integer batchSize;
	Integer threads;
	String consistentSnapshotFlag;
	String queuedURIsFlag;
	String runMode;
	String nodeName;
	BatchRDBMSConfig rdbmsConfig;
	BatchAMQPConfig amqpConfig;
	BatchResultConfig resultConfig;
	DatabaseConfig dbConfig;
	BatchFileExportConfig fileConfig;
	
	public String getQuery() {
		return query;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	public Integer getBatchSize() {
		return batchSize;
	}
	public void setBatchSize(Integer batchSize) {
		this.batchSize = batchSize;
	}
	public Integer getThreads() {
		return threads;
	}
	public void setThreads(Integer threads) {
		this.threads = threads;
	}
	
	public BatchResultConfig getResultConfig() {
		return resultConfig;
	}
	public void setResultConfig(BatchResultConfig resultConfig) {
		this.resultConfig = resultConfig;
	}
	public DatabaseConfig getDbConfig() {
		return dbConfig;
	}
	public void setDbConfig(DatabaseConfig dbConfig) {
		this.dbConfig = dbConfig;
	}
	public String getSerialized() {
		return serialized;
	}
	public void setSerialized(String serialized) {
		this.serialized = serialized;
	}
	public BatchRDBMSConfig getRdbmsConfig() {
		return rdbmsConfig;
	}
	public void setRdbmsConfig(BatchRDBMSConfig rdbmsConfig) {
		this.rdbmsConfig = rdbmsConfig;
	}
	public String getConsistentSnapshotFlag() {
		return consistentSnapshotFlag;
	}
	public void setConsistentSnapshotFlag(String consistentSnapshotFlag) {
		this.consistentSnapshotFlag = consistentSnapshotFlag;
	}
	public String getQueuedURIsFlag() {
		return queuedURIsFlag;
	}
	public void setQueuedURIsFlag(String queuedURIsFlag) {
		this.queuedURIsFlag = queuedURIsFlag;
	}
	public BatchAMQPConfig getAmqpConfig() {
		return amqpConfig;
	}
	public void setAmqpConfig(BatchAMQPConfig amqpConfig) {
		this.amqpConfig = amqpConfig;
	}
	public BatchFileExportConfig getFileConfig() {
		return fileConfig;
	}
	public void setFileConfig(BatchFileExportConfig fileConfig) {
		this.fileConfig = fileConfig;
	}
	public String getRunMode() {
		return runMode;
	}
	public void setRunMode(String runMode) {
		this.runMode = runMode;
	}
	public String getNodeName() {
		return nodeName;
	}
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}
	
}
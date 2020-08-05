package com.marklogic.batch.config;

import java.util.Date;

public class DeleteConfig {
	String query;
	String [] collections;
	Integer batchSize;
	Integer threads;
	DeleteResultConfig resultConfig;
	DatabaseConfig dbConfig;
	
	public String getQuery() {
		return query;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	public String[] getCollections() {
		return collections;
	}
	public void setCollections(String[] collections) {
		this.collections = collections;
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
	
	public DeleteResultConfig getResultConfig() {
		return resultConfig;
	}
	public void setResultConfig(DeleteResultConfig resultConfig) {
		this.resultConfig = resultConfig;
	}
	public DatabaseConfig getDbConfig() {
		return dbConfig;
	}
	public void setDbConfig(DatabaseConfig dbConfig) {
		this.dbConfig = dbConfig;
	}
	

}
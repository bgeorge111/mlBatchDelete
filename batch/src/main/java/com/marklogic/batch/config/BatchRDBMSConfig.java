package com.marklogic.batch.config;

public class BatchRDBMSConfig {
	
	String connectionUrl;
	String csvTransform;
	String sql;
	public String getConnectionUrl() {
		return connectionUrl;
	}
	public void setConnectionUrl(String connectionUrl) {
		this.connectionUrl = connectionUrl;
	}
	public String getCsvTransform() {
		return csvTransform;
	}
	public void setCsvTransform(String csvTransform) {
		this.csvTransform = csvTransform;
	}
	public String getSql() {
		return sql;
	}
	public void setSql(String sql) {
		this.sql = sql;
	}
	
	

}

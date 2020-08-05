package com.marklogic.batch.result;

public class ResultObject {

	String batchResultUri ; 
	Long batchResultCount;
	Long batchDuration;
	String batchJobDescription;
	String batchJobId;
	String batchJobName;
	String batchJobType;
	String batchJobStartTs;
	String batchJobEndTs;
	String query;
	public String getBatchResultUri() {
		return batchResultUri;
	}
	public void setBatchResultUri(String batchResultUri) {
		this.batchResultUri = batchResultUri;
	}
	public Long getBatchResultCount() {
		return batchResultCount;
	}
	public void setBatchResultCount(Long batchResultCount) {
		this.batchResultCount = batchResultCount;
	}
	public Long getBatchDuration() {
		return batchDuration;
	}
	public void setBatchDuration(Long batchDuration) {
		this.batchDuration = batchDuration;
	}
	public String getBatchJobDescription() {
		return batchJobDescription;
	}
	public void setBatchJobDescription(String batchJobDescription) {
		this.batchJobDescription = batchJobDescription;
	}
	public String getBatchJobId() {
		return batchJobId;
	}
	public void setBatchJobId(String batchJobId) {
		this.batchJobId = batchJobId;
	}
	public String getBatchJobName() {
		return batchJobName;
	}
	public void setBatchJobName(String batchJobName) {
		this.batchJobName = batchJobName;
	}
	public String getBatchJobType() {
		return batchJobType;
	}
	public void setBatchJobType(String batchJobType) {
		this.batchJobType = batchJobType;
	}
	public String getBatchJobStartTs() {
		return batchJobStartTs;
	}
	public void setBatchJobStartTs(String batchJobStartTs) {
		this.batchJobStartTs = batchJobStartTs;
	}
	public String getBatchJobEndTs() {
		return batchJobEndTs;
	}
	public void setBatchJobEndTs(String batchJobEndTs) {
		this.batchJobEndTs = batchJobEndTs;
	}
	public String getQuery() {
		return query;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	

	
}

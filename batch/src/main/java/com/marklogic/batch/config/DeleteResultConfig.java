package com.marklogic.batch.config;

public class DeleteResultConfig {

	String resulttype;
	String location;
	String [] collections;
	String [] permissions;
	String prefix;
	public String getResulttype() {
		return resulttype;
	}
	public void setResulttype(String resulttype) {
		this.resulttype = resulttype;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	public String[] getCollections() {
		return collections;
	}
	public void setCollections(String[] collections) {
		this.collections = collections;
	}
	public String[] getPermissions() {
		return permissions;
	}
	public void setPermissions(String[] permissions) {
		this.permissions = permissions;
	}
	public String getPrefix() {
		return prefix;
	}
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}
	
}

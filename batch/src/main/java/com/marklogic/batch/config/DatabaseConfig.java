package com.marklogic.batch.config;

public class DatabaseConfig {
	
	String mlHost;
	Integer mlPort;
	String mlUserName;
	String mlPassword;
	String mlDatabase;
	String auth;
	String connectionType;
	String simpleSsl;
	String customSsl;
	String tlsVersion;
	String certPath;
	String certPwd;
	String externalName;
	String hostNameVerifier;
	String mutualAuth;
	
	public String getMlHost() {
		return mlHost;
	}
	public void setMlHost(String mlHost) {
		this.mlHost = mlHost;
	}
	public Integer getMlPort() {
		return mlPort;
	}
	public void setMlPort(Integer mlPort) {
		this.mlPort = mlPort;
	}
	public String getMlUserName() {
		return mlUserName;
	}
	public void setMlUserName(String mlUserName) {
		this.mlUserName = mlUserName;
	}
	public String getMlPassword() {
		return mlPassword;
	}
	public void setMlPassword(String mlPassword) {
		this.mlPassword = mlPassword;
	}
	public String getMlDatabase() {
		return mlDatabase;
	}
	public void setMlDatabase(String mlDatabase) {
		this.mlDatabase = mlDatabase;
	}
	public String getAuth() {
		return auth;
	}
	public void setAuth(String auth) {
		this.auth = auth;
	}
	public String getSimpleSsl() {
		return simpleSsl;
	}
	public void setSimpleSsl(String simpleSsl) {
		this.simpleSsl = simpleSsl;
	}
	public String getConnectionType() {
		return connectionType;
	}
	public void setConnectionType(String connectionType) {
		this.connectionType = connectionType;
	}
	public String getCustomSsl() {
		return customSsl;
	}
	public void setCustomSsl(String customSsl) {
		this.customSsl = customSsl;
	}
	public String getTlsVersion() {
		return tlsVersion;
	}
	public void setTlsVersion(String tlsVersion) {
		this.tlsVersion = tlsVersion;
	}
	public String getCertPath() {
		return certPath;
	}
	public void setCertPath(String certPath) {
		this.certPath = certPath;
	}
	public String getCertPwd() {
		return certPwd;
	}
	public void setCertPwd(String certPwd) {
		this.certPwd = certPwd;
	}
	public String getExternalName() {
		return externalName;
	}
	public void setExternalName(String externalName) {
		this.externalName = externalName;
	}
	public String getHostNameVerifier() {
		return hostNameVerifier;
	}
	public void setHostNameVerifier(String hostNameVerifier) {
		this.hostNameVerifier = hostNameVerifier;
	}
	public String getMutualAuth() {
		return mutualAuth;
	}
	public void setMutualAuth(String mutualAuth) {
		this.mutualAuth = mutualAuth;
	}
	

}

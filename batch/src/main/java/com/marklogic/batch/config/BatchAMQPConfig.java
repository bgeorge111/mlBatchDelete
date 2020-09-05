package com.marklogic.batch.config;

public class BatchAMQPConfig {
	
	String exchange;
	String routingKey;
	String msgAppId;
	
	public String getExchange() {
		return exchange;
	}
	public void setExchange(String exchange) {
		this.exchange = exchange;
	}
	public String getRoutingKey() {
		return routingKey;
	}
	public void setRoutingKey(String routingKey) {
		this.routingKey = routingKey;
	}
	public String getMsgAppId() {
		return msgAppId;
	}
	public void setMsgAppId(String msgAppId) {
		this.msgAppId = msgAppId;
	}
	
	

}

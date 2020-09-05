package com.marklogic.batch.lock;

import java.util.Date;

public class Lock {
	String serviceId;
	String lockId;
	Date lockStartTs;
	Date lockEndTs;
	Date noLockAfterTs;
	String lockedBy;
	String status;
	public String getServiceId() {
		return serviceId;
	}
	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}
	public String getLockId() {
		return lockId;
	}
	public void setLockId(String lockId) {
		this.lockId = lockId;
	}
	public Date getLockStartTs() {
		return lockStartTs;
	}
	public void setLockStartTs(Date lockStartTs) {
		this.lockStartTs = lockStartTs;
	}
	public Date getLockEndTs() {
		return lockEndTs;
	}
	public void setLockEndTs(Date lockEndTs) {
		this.lockEndTs = lockEndTs;
	}
	public Date getNoLockAfterTs() {
		return noLockAfterTs;
	}
	public void setNoLockAfterTs(Date noLockAfterTs) {
		this.noLockAfterTs = noLockAfterTs;
	}
	public String getLockedBy() {
		return lockedBy;
	}
	public void setLockedBy(String lockedBy) {
		this.lockedBy = lockedBy;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	
	
	
}

package com.marklogic.batch.lock;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.StandardEnvironment;

import com.marklogic.batch.utils.ConnectionUtils;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.admin.ServerConfigurationManager;
import com.marklogic.client.admin.ServerConfigurationManager.UpdatePolicy;
import com.marklogic.client.document.DocumentDescriptor;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.JacksonDatabindHandle;

public class LockProvider {
	private final static Logger logger = LoggerFactory.getLogger(ConnectionUtils.class);
	private StandardEnvironment ENV;

	private void resetConfiguration(DatabaseClient dbClient) {
		ServerConfigurationManager configMgr = dbClient.newServerConfigManager();
		configMgr.readConfiguration();
		configMgr.setUpdatePolicy(UpdatePolicy.MERGE_METADATA);
		configMgr.writeConfiguration();
	}

	private void setConfiguration(DatabaseClient dbClient) {
		ServerConfigurationManager configMgr = dbClient.newServerConfigManager();
		configMgr.readConfiguration();
		configMgr.setUpdatePolicy(UpdatePolicy.VERSION_REQUIRED);
		configMgr.writeConfiguration();
	}

	public int lockService(DatabaseClient dbClient, String serviceId, String nodeName) {
		/*
		 * The below implementation can be improved with a firm locking on the lock
		 * document.
		 */
		String pattern = "yyyy-MM-dd HH:mm:ss.SSSZ";
		String lockId = UUID.randomUUID().toString();
		String docId = "/batch/locks/" + serviceId + ".json";
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		JSONDocumentManager docMgr = dbClient.newJSONDocumentManager();
		DocumentMetadataHandle meta = new DocumentMetadataHandle();
		meta.withCollections("locks");
		Lock currentLock = new Lock();
		Lock newLock = new Lock();
		JacksonDatabindHandle<Lock> readHandle = new JacksonDatabindHandle<Lock>(currentLock);
		setConfiguration(dbClient);
		DocumentDescriptor desc = docMgr.newDescriptor(docId);
		desc = docMgr.exists(docId);
		if (desc != null) {
			docMgr.read(desc, readHandle);
			if ("Locked".equalsIgnoreCase(readHandle.get().getStatus())) {
				logger.warn("Lock Service:: Document " + docId + " already in locked status.");
				resetConfiguration(dbClient);
				return -1;
			} else {
				Date currentTime = new Date();
				Calendar cal = Calendar.getInstance();
				cal.setTime(currentTime);
				cal.add(Calendar.MINUTE, 60);
				newLock.setServiceId(serviceId);
				newLock.setLockedBy(nodeName);
				newLock.setLockId(lockId);
				newLock.setLockStartTs(currentTime);
				newLock.setNoLockAfterTs(cal.getTime());
				newLock.setLockEndTs(null);
				newLock.setStatus("Locked");
				JacksonDatabindHandle<Lock> writeHandle = new JacksonDatabindHandle<Lock>(newLock);
				docMgr.write(desc, writeHandle);
				logger.warn("Lock Service:: Document " + docId + " updated to locked status.");
				resetConfiguration(dbClient);
			}
		} else {
			Date currentTime = new Date();
			Calendar cal = Calendar.getInstance();
			cal.setTime(currentTime);
			cal.add(Calendar.MINUTE, 60);
			newLock.setServiceId(serviceId);
			newLock.setLockedBy(nodeName);
			newLock.setLockId(lockId);
			newLock.setLockStartTs(currentTime);
			newLock.setNoLockAfterTs(cal.getTime());
			newLock.setLockEndTs(null);
			newLock.setStatus("Locked");
			JacksonDatabindHandle<Lock> writeHandle = new JacksonDatabindHandle<Lock>(newLock);
			docMgr.write(docId, writeHandle);
			logger.warn("Lock Service:: Document " + docId + " created with locked status.");
			resetConfiguration(dbClient);
		}
		return 0;
	}

	public int unLockService(DatabaseClient dbClient, String serviceId, String nodeName) {
		/*
		 * The below implementation can be improved with a firm locking on the lock
		 * document.
		 */
		String pattern = "yyyy-MM-dd HH:mm:ss.SSSZ";
		String docId = "/batch/locks/" + serviceId + ".json";
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		JSONDocumentManager docMgr = dbClient.newJSONDocumentManager();
		DocumentMetadataHandle meta = new DocumentMetadataHandle();
		meta.withCollections("locks");
		Lock currentLock = new Lock();
		Lock newLock = new Lock();
		JacksonDatabindHandle<Lock> readHandle = new JacksonDatabindHandle<Lock>(currentLock);
		setConfiguration(dbClient);
		DocumentDescriptor desc = docMgr.newDescriptor(docId);
		desc = docMgr.exists(docId);
		if (desc != null) {
			docMgr.read(desc, readHandle);
			if ("Available".equalsIgnoreCase(readHandle.get().getStatus())) {
				logger.warn("Lock Service:: Document " + docId + " already in Available status.");
				resetConfiguration(dbClient);
				return -1;
			} else {
				Date currentTime = new Date();
				Calendar cal = Calendar.getInstance();
				cal.setTime(currentTime);
				cal.add(Calendar.MINUTE, 60);
				newLock.setServiceId(serviceId);
				newLock.setLockedBy(nodeName);
				newLock.setLockId(null);
				newLock.setLockStartTs(null);
				newLock.setNoLockAfterTs(null);
				newLock.setLockEndTs(currentTime);
				newLock.setStatus("Available");
				JacksonDatabindHandle<Lock> writeHandle = new JacksonDatabindHandle<Lock>(newLock);
				docMgr.write(desc, writeHandle);
				logger.warn("UnLock Service:: Document " + docId + " updated to Available status.");
				resetConfiguration(dbClient);
			}
		} else {
			Date currentTime = new Date();
			Calendar cal = Calendar.getInstance();
			cal.setTime(currentTime);
			cal.add(Calendar.MINUTE, 60);
			newLock.setServiceId(serviceId);
			newLock.setLockedBy(nodeName);
			newLock.setLockId(null);
			newLock.setLockStartTs(null);
			newLock.setNoLockAfterTs(null);
			newLock.setLockEndTs(currentTime);
			newLock.setStatus("Available");
			JacksonDatabindHandle<Lock> writeHandle = new JacksonDatabindHandle<Lock>(newLock);
			docMgr.write(docId, writeHandle);
			logger.warn("UnLock Service:: Document " + docId
					+ " created with Available status. Creation was not expected here although");
			resetConfiguration(dbClient);
		}
		return 0;
	}

}

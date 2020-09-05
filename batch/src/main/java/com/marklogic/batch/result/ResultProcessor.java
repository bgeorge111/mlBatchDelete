package com.marklogic.batch.result;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.marklogic.batch.config.BatchConfig;
import com.marklogic.batch.delete.processor.DeleteProcessor;
import com.marklogic.batch.utils.GenUtils;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.JacksonDatabindHandle;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;

@Component
public class ResultProcessor {
	private final Logger logger = LoggerFactory.getLogger(DeleteProcessor.class);
	@Autowired
	protected JSONDocumentManager docMgr;
	
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

}

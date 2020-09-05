package com.marklogic.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.context.ShutdownEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.marklogic.batch.delete.processor.DeleteProcessor;
import com.marklogic.batch.export.processor.AMQPExportProcessor;
import com.marklogic.batch.export.processor.FileExportProcessor;
import com.marklogic.batch.export.processor.SQLServerExportProcessor;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Configuration
@ConditionalOnProperty(name = "run.mode", havingValue="online")
public class OnlineRunner {

	@Value("${run.mode:batch}")
	private String mode;
	@Value("${static.batch.export.amqp.enabled:false}")
	private String amqpFlag;
	@Value("${static.batch.export.kafka.enabled:false}")
	private String kafkaFlag;
	@Value("${static.batch.export.rdbms.enabled:false}")
	private String rdbmsFlag;
	@Value("${static.batch.export.file.enabled:true}")
	private String fileFlag;
	@Value("${static.batch.delete.enabled:false}")
	private String deleteFlag;
	
    private static final Logger logger = LoggerFactory.getLogger(OnlineRunner.class);

    @Autowired
    private ConfigurableApplicationContext cac;
	
	public void runOnLineTask() throws IOException, SQLException {
		if ("ONLINE".equalsIgnoreCase(mode)) {
			logger.warn("Running in Online Mode...");
			if ("TRUE".equalsIgnoreCase(deleteFlag)) {
				logger.warn("Starting Delete Processor");
				DeleteProcessor deleteBean = cac.getBean(DeleteProcessor.class);
				deleteBean.processBatchDelete();
				logger.warn("Completed Delete Processor");
			}
			if ("TRUE".equalsIgnoreCase(rdbmsFlag)) {
				logger.warn("Starting SQLServer Export Processor");
				SQLServerExportProcessor rdbmsExportBean = cac.getBean(SQLServerExportProcessor.class);
				rdbmsExportBean.processRDBMSExport();
				logger.warn("Completed SQLServer Export Processor");
			}
			if ("TRUE".equalsIgnoreCase(amqpFlag)) {
				logger.warn("Starting AMQP Export Processor");
				AMQPExportProcessor amqpExportBean = cac.getBean(AMQPExportProcessor.class);
				amqpExportBean.processAMQPExport();
				logger.warn("Completed AMQP Export Processor");
			}
			if ("TRUE".equalsIgnoreCase(fileFlag)) {
				logger.warn("Starting File Export Processor");
				FileExportProcessor fileExportBean = cac.getBean(FileExportProcessor.class);
				fileExportBean.processFileExport();
				logger.warn("Completed File Export Processor");
			}
			logger.warn("Completed the Run in Online Mode..."); 
		}
	}

    public void init() throws IOException, SQLException {
    	runOnLineTask();
    	}
   
}
package com.marklogic.batch;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Properties;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.marklogic.batch.config.DatabaseConfig;
import com.marklogic.batch.processor.DeleteProcessor;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.DataMovementManager;
import com.marklogic.client.document.DocumentManager;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.client.query.QueryManager;
import com.marklogic.utils.ConnectionUtils;;

@SpringBootApplication
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.marklogic.batch")
@EnableAsync
@EnableScheduling
//@PropertySource(value = { "classpath:user.properties" }, ignoreResourceNotFound = true)
@PropertySource(value = "file:user.properties", factory = ReloadablePropertySourceFactory.class)
public class BatchApplication {

	@Value("${static.marklogic.host}")
	private String host;
	@Value("${static.marklogic.port}")
	private int port;
	@Value("${static.marklogic.username}")
	private String username;
	@Value("${static.marklogic.password}")
	private String password;
	@Value("${static.marklogic.database}")
	private String database;
	@Value("${static.marklogic.auth}")
	private String auth;
	@Value("${static.marklogic.simpleSsl}")
	private String simpleSsl;
	@Value("${static.marklogic.connection.type}")
	private String connType;
	@Value("${static.marklogic.customSsl}")
	private String customSsl;
	@Value("${static.marklogic.mutualAuth}")
	private String mutualAuth;
	@Value("${static.marklogic.certFile}")
	private String certFile;
	@Value("${static.marklogic.certPassword}")
	private String certPassword;
	@Value("${static.marklogic.externalName}")
	private String externalName;
	@Value("${static.marklogic.hostNameVerifier}")
	private String hostNameVerifier;
	@Value("${static.marklogic.tlsVersion}")
	private String tlsVersion;
	@Value("${dynamic.batch.delete.collections}")
	private String collections;
	@Value("${dynamic.batch.delete.threads}")
	private String deleteThreads;
	@Value("${dynamic.batch.delete.batchSize}")
	private String batchSize;
	@Value("${dynamic.batch.delete.query}")
	private String deleteQuery;
	@Value("${logging.level.root}")
	private String LOGLEVEL;
	@Value("${result.delete.type}")
	private String RESULT_TYPE;
	@Value("${result.delete.collections}")
	private String RESULT_COLLECTIONS;
	@Value("${result.delete.permissions}")
	private String RESULT_PERMISSIONS;
	@Value("${result.delete.uri.prefix}")
	private String RESULT_URI_PREFIX;
	@Value("${result.delete.file.location}")
	private String RESULT_FILE_LOCATION;

	private final static Logger logger = LoggerFactory.getLogger(DeleteProcessor.class);
	
	DatabaseClient dbClient = null;

	@Bean
	public DatabaseConfig buildDatabaseConfig() {
		DatabaseConfig config = new DatabaseConfig();
		logger.info("Building Database Config");
		config.setMlHost(host);
		config.setMlPort(port);
		config.setMlDatabase(database);
		config.setAuth(auth);
		config.setSimpleSsl(simpleSsl);
		config.setMlUserName(username);
		config.setMlPassword(password);
		config.setConnectionType(connType);
		config.setCertPath(certFile);
		config.setCertPwd(certPassword);
		config.setCustomSsl(customSsl);
		config.setExternalName(externalName);
		config.setHostNameVerifier(hostNameVerifier);
		config.setTlsVersion(tlsVersion);
		config.setMutualAuth(mutualAuth);

		return config;
	}

	@Bean
	@DependsOn({ "buildDatabaseConfig" })
	public QueryManager getQueryManager(@Autowired DatabaseConfig config) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException, IOException{
		logger.info("Building Query Manager");
		return dbClient.newQueryManager();
	}
	
	@Bean
	@DependsOn({ "buildDatabaseConfig" })
	public JSONDocumentManager getJSONDocumentManager(@Autowired DatabaseConfig config) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException, IOException {
		logger.info("Building JSON Document Manager");
		return dbClient.newJSONDocumentManager();
	}

	@Bean
	@DependsOn({ "buildDatabaseConfig" })
	public DataMovementManager getDataMovementManager(@Autowired DatabaseConfig config) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException, IOException{
		logger.info("Building Data Movement Manager");
		return dbClient.newDataMovementManager();
	}

	@Bean
	@DependsOn({ "buildDatabaseConfig" })
	public DatabaseClient getDatabaseClient(@Autowired DatabaseConfig config) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException {
		logger.info("Building Database Client");
		dbClient = ConnectionUtils.getDatabaseClient(config); 
		return dbClient;
	}

	@Bean
	@ConditionalOnProperty(name = "spring.config.location", matchIfMissing = false)
	public PropertiesConfiguration propertiesConfiguration(@Value("${spring.config.location}") String path)
			throws Exception {
		String filePath = new File(path.substring("file:".length())).getCanonicalPath();
		PropertiesConfiguration configuration = new PropertiesConfiguration(new File(filePath));
		FileChangedReloadingStrategy fileChangedReloadingStrategy = new FileChangedReloadingStrategy();
		fileChangedReloadingStrategy.setRefreshDelay(5000);
		configuration.setReloadingStrategy(fileChangedReloadingStrategy);
		return configuration;
	}

	@Bean
	@ConditionalOnBean(PropertiesConfiguration.class)
	@Primary
	public Properties properties(PropertiesConfiguration propertiesConfiguration) throws Exception {
		ReloadableProperties properties = new ReloadableProperties(propertiesConfiguration);
		return properties;
	}

	/*
	 * Entry point to the application
	 */

	public static void main(String[] args) {

		ConfigurableApplicationContext cac = SpringApplication.run(BatchApplication.class, args);
		logger.info ("Starting application..");
		DeleteProcessor deleteBean = cac.getBean(DeleteProcessor.class);
		deleteBean.processBatchDelete();
	}

}

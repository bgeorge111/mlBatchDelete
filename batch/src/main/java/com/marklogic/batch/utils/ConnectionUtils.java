package com.marklogic.batch.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marklogic.batch.config.DatabaseConfig;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClient.ConnectionType;
import com.marklogic.client.DatabaseClientFactory.SSLHostnameVerifier;
import com.marklogic.client.DatabaseClientFactory.SecurityContext;
import static com.marklogic.batch.utils.GenUtils.isNotEmpty;



@Component
public class ConnectionUtils {

	private final static Logger logger = LoggerFactory.getLogger(ConnectionUtils.class);

	protected static SSLHostnameVerifier getHostNameVerifier(String sslHostNameVerifier) {
		if ("ANY".equals(sslHostNameVerifier))
			return DatabaseClientFactory.SSLHostnameVerifier.ANY;
		else if ("COMMON".equals(sslHostNameVerifier))
			return DatabaseClientFactory.SSLHostnameVerifier.COMMON;
		else if ("STRICT".equals(sslHostNameVerifier))
			return DatabaseClientFactory.SSLHostnameVerifier.STRICT;
		else
			return DatabaseClientFactory.SSLHostnameVerifier.ANY;
	}

	protected static ConnectionType getConnectionType(String connectionType) {
		if ("DIRECT".equals(connectionType))
			return ConnectionType.DIRECT;
		else if ("GATEWAY".equals(connectionType))
			return ConnectionType.GATEWAY;
		else
			return ConnectionType.DIRECT;
	}

	protected static DatabaseClient getCustomSSLDatabaseClient(DatabaseConfig config)
			throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException,
			CertificateException, UnrecoverableKeyException, KeyManagementException {
		DatabaseClient client = null;
		SSLContext sslContext = null;
		SecurityContext secCtx = null;
		TrustManager[] trust = null;
		KeyManager[] key = null;
		String sslkeyStorePassword = config.getKeystorePwd().trim();
		String ssltrustStorePassword = config.getTruststorePwd().trim();
		KeyStore clientKeyStore = null;
		KeyStore clientTrustStore = null;
		if ("TRUE".equalsIgnoreCase(config.getMutualAuth())) {
			/* 2 way ssl */
			if (isNotEmpty(config.getKeystoreType())) {
				clientKeyStore = KeyStore.getInstance(config.getKeystoreType());
				char[] sslkeyStorePasswordChars = sslkeyStorePassword != null ? sslkeyStorePassword.toCharArray() : null;
				try (InputStream keystoreInputStream = new FileInputStream(config.getKeystorePath())) {
					clientKeyStore.load(keystoreInputStream, sslkeyStorePasswordChars);
				}
				KeyManagerFactory keyManagerFactory = KeyManagerFactory
						.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				keyManagerFactory.init(clientKeyStore, config.getCertPwd().toCharArray());
				key = keyManagerFactory.getKeyManagers();
			}
			
			if (isNotEmpty(config.getTruststoreType())) {
				clientTrustStore = KeyStore.getInstance(config.getTruststoreType());
				char[] ssltrustStorePasswordChars = ssltrustStorePassword != null ? ssltrustStorePassword.toCharArray()
						: null;
				try (InputStream keystoreInputStream = new FileInputStream(config.getTruststorePath())) {
					clientTrustStore.load(keystoreInputStream, ssltrustStorePasswordChars);
				}
				TrustManagerFactory trustManagerFactory = TrustManagerFactory
						.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				trustManagerFactory.init(clientTrustStore);
				trust = trustManagerFactory.getTrustManagers();
			}

			sslContext = SSLContext.getInstance(config.getTlsVersion());
			sslContext.init(key, trust, null);
			logger.warn("Creating 2wayssl Database Client");
		} else {
			logger.warn("Creating 1wayssl Database Client");
			sslContext = SSLContext.getDefault();
		}

		if (config.getAuth().compareToIgnoreCase("BASIC") == 0) {
			secCtx = new DatabaseClientFactory.BasicAuthContext(config.getMlUserName(), config.getMlPassword())
					.withSSLContext(sslContext, (X509TrustManager) trust[0])
					.withSSLHostnameVerifier(getHostNameVerifier(config.getHostNameVerifier()));
			client = DatabaseClientFactory.newClient(config.getMlHost(), config.getMlPort(), config.getMlDatabase(),
					secCtx, getConnectionType(config.getConnectionType()));
		}

		if (config.getAuth().compareToIgnoreCase("DIGEST") == 0) {
			secCtx = new DatabaseClientFactory.DigestAuthContext(config.getMlUserName(), config.getMlPassword())
					.withSSLContext(sslContext, (X509TrustManager) trust[0])
					.withSSLHostnameVerifier(getHostNameVerifier(config.getHostNameVerifier()));
			client = DatabaseClientFactory.newClient(config.getMlHost(), config.getMlPort(), config.getMlDatabase(),
					secCtx, getConnectionType(config.getConnectionType()));
		}
		logger.warn("Created Custom SSL Database Client");
		return client;
	}

	protected static DatabaseClient getCertificateDatabaseClient(DatabaseConfig config)
			throws UnrecoverableKeyException, KeyManagementException, CertificateException, IOException {
		DatabaseClient client = null;
		client = DatabaseClientFactory.newClient(config.getMlHost(), config.getMlPort(), config.getMlDatabase(),
				new DatabaseClientFactory.CertificateAuthContext(config.getCertPath(), config.getCertPwd(),
						new SimpleX509TrustManager())
								.withSSLHostnameVerifier(getHostNameVerifier(config.getHostNameVerifier())),
				getConnectionType(config.getConnectionType()));
		logger.warn("Created Certificate based Client");
		return client;
	}

	protected static DatabaseClient getSimpleSSLDatabaseClient(DatabaseConfig config)
			throws KeyManagementException, NoSuchAlgorithmException {
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		TrustManager[] trust = new TrustManager[] { new SimpleX509TrustManager() };
		sslContext.init(null, trust, null);

		DatabaseClient client = null;

		if (config.getAuth().compareToIgnoreCase("BASIC") == 0) {
			client = DatabaseClientFactory.newClient(config.getMlHost(), config.getMlPort(), config.getMlDatabase(),
					new DatabaseClientFactory.BasicAuthContext(config.getMlUserName(), config.getMlPassword())
							.withSSLContext(sslContext, new SimpleX509TrustManager()).withSSLHostnameVerifier(
									SSLHostnameVerifier.ANY),
					getConnectionType(config.getConnectionType()));
		}
		if (config.getAuth().compareToIgnoreCase("DIGEST") == 0) {
			client = DatabaseClientFactory.newClient(config.getMlHost(), config.getMlPort(), config.getMlDatabase(),
					new DatabaseClientFactory.BasicAuthContext(config.getMlUserName(), config.getMlPassword())
							.withSSLContext(sslContext, new SimpleX509TrustManager()).withSSLHostnameVerifier(
									SSLHostnameVerifier.ANY),
					getConnectionType(config.getConnectionType()));
		}

		logger.warn("Created Simple SSL Client");
		return client;
	}

	public static DatabaseClient getDatabaseClient(DatabaseConfig config) throws KeyManagementException,
			NoSuchAlgorithmException, UnrecoverableKeyException, CertificateException, IOException, KeyStoreException {
		DatabaseClient client = null;

		/*
		 * If simpleSsl is true, return a simple SSL client. All other configurations,
		 * even conflicting are ignored.
		 */
		if (config.getSimpleSsl().compareToIgnoreCase("TRUE") == 0) {
			logger.warn("Creating Simple SSL Client");
			return getSimpleSSLDatabaseClient(config);
		}

		/*
		 * Certificate Authentication.
		 */
		if (config.getAuth().compareToIgnoreCase("CERTIFICATE") == 0) {
			logger.warn("Creating Certificate based Client");
			return getCertificateDatabaseClient(config);
		}

		/*
		 * If customSsl is true, then Host verifier, TLS version, mutual auth or 1way
		 * auth etc are checked in side the method getCustomSSLDatabaseClient.
		 */
		if (config.getCustomSsl().compareToIgnoreCase("TRUE") == 0) {
			logger.warn("Creating Custom SSL Client");
			return getCustomSSLDatabaseClient(config);
		}

		/*
		 * If none of the above was true, then it is a no SSL client with BASIC or
		 * DIGEST.
		 */
		logger.warn("Creating non-SSL Database Client");
		if (config.getAuth().compareToIgnoreCase("BASIC") == 0) {
			client = DatabaseClientFactory.newClient(config.getMlHost(), config.getMlPort(), config.getMlDatabase(),
					new DatabaseClientFactory.BasicAuthContext(config.getMlUserName(), config.getMlPassword()),
					getConnectionType(config.getConnectionType()));
		}
		if (config.getAuth().compareToIgnoreCase("DIGEST") == 0) {
			client = DatabaseClientFactory.newClient(config.getMlHost(), config.getMlPort(), config.getMlDatabase(),
					new DatabaseClientFactory.DigestAuthContext(config.getMlUserName(), config.getMlPassword()),
					getConnectionType(config.getConnectionType()));
		}
		return client;
	}
}

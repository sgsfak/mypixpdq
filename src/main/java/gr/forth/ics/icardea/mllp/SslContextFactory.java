package gr.forth.ics.icardea.mllp;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;
import java.util.Enumeration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class SslContextFactory {
	private static final String PROTOCOL = "TLS";
	private SSLContext SERVER_CONTEXT;

	private SslContextFactory() { }
	/**
	 * SingletonHolder is loaded on the first execution of Singleton.getInstance() 
	 * or the first access to SingletonHolder.INSTANCE, not before.
	 */
	private static class SingletonHolder { 
		public static final SslContextFactory instance = new SslContextFactory();
	}

	public static SslContextFactory getInstance() {
		return SingletonHolder.instance;
	}

	public void init(String keyStoreFileName, String keyStorePass) {

		// Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
		if (algorithm == null) {
			algorithm = "SunX509";
		}

		char[] pass = keyStorePass.toCharArray();
		SSLContext serverContext = null;
		// SSLContext clientContext = null;
		try {
			KeyStore ks = KeyStore.getInstance("JKS");
			InputStream s = new FileInputStream(new File(keyStoreFileName));
			ks.load(s,pass);
			s.close();
			/*
			for (Enumeration<String> it = ks.aliases(); it.hasMoreElements();) {
				System.out.println("ALIAS:" + it.nextElement());
			}
			*/
			// Set up key manager factory to use our key store
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
			kmf.init(ks, pass); // We assume that the same password was used for the key!!!

			// Initialize the SSLContext to work with our key managers.        	
			serverContext = SSLContext.getInstance(PROTOCOL);
			serverContext.init(kmf.getKeyManagers(), null, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error(
					"Failed to initialize the server-side SSLContext", e);
		}

		SERVER_CONTEXT = serverContext;
	}

	public SSLContext getServerContext() {
		return SERVER_CONTEXT;
	}
}

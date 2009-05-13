package groovyx.net.http

import static groovyx.net.http.Method.HEAD

import org.junit.Testimport java.security.KeyStoreimport org.apache.http.conn.scheme.Schemeimport org.apache.http.conn.ssl.SSLSocketFactory;

/**
 * @author tnichols
 */
public class SSLTest {

	def uri = "https://www.dev.java.net/" ;
	
	@Test public void testTrustedCert() {
		def http = new HTTPBuilder( uri )

		def keyStore = KeyStore.getInstance( KeyStore.defaultType )
		
		getClass().getResource( "/truststore.jks" ).withInputStream {
		   keyStore.load( it, "test1234".toCharArray() )
		}

		http.client.connectionManager.schemeRegistry.register( 
				new Scheme("https", new SSLSocketFactory(keyStore), 443) )
				
		def status = http.request( HEAD ) {
			response.success = { it.statusLine.statusCode }
		}
		assert status == 200
	}	
}

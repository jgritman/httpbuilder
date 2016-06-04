package groovyx.net.http

import java.security.KeyStore;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import spock.lang.*;
import static groovyx.net.http.Method.HEAD;

/**
 * @author tnichols
 */
class SSLTest extends Specification {

    def uri = "https://dev.java.net/" ;

    @Ignore
    def "Trusted Cert"() {
        def http = new HTTPBuilder( uri )

        def keyStore = KeyStore.getInstance( KeyStore.defaultType )

        getClass().getResource( "/truststore.jks" ).withInputStream {
           keyStore.load( it, "test1234".toCharArray() )
        }

        final socketFactory = new SSLSocketFactory(keyStore)
        socketFactory.hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER

        http.client.connectionManager.schemeRegistry.register(
                new Scheme("https", socketFactory, 443) )

        def status = http.request( HEAD ) {
            response.success = { it.statusLine.statusCode }
            response.failure = { it.statusLine.statusCode }
        }
        // dev.java.net doesn't exist anymore, but the server will redirect
        // to http://java.net/project/dev which is a 404 page.
        // but we still won't get the PeerUnverifiedException which is what
        // we're attempting to achieve.
        assert status == 404 // 200
    }
}

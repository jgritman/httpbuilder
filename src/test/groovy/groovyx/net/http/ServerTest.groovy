package groovyx.net.http;

import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

import org.apache.http.params.HttpConnectionParams;
import org.junit.Test;

public class ServerTest {
	@Test
	public void testDummy() {}
	
//	@Test
	public void testAlternateParsing() {
		
		println "-----------TESTING self-server"
		def request = new StringBuilder()
		def done = false
		Thread.start {
			println "- Thread running"
			def ss
			try {
				ss = new ServerSocket(11234)
				while ( ! done ) {
					ss.accept { sock ->
						println "- connected"
						sock.soTimeout = 10000
						sock.withStreams { input, output ->
							def reader = new BufferedReader( new InputStreamReader(input))
							def line = reader.readLine()
							def entityLength = null
							while ( line != '' ) { 
//								println "+++ $line"
								request.append "$line\n"
								def contentLengthMatcher = line =~ /(?i)^content-length:\s+(\d+)$/
								if ( contentLengthMatcher.size() ) 
									entityLength = contentLengthMatcher[0][1] as int
								line = reader.readLine()
							}
							def requestEntity = null
							if ( entityLength ) {
								requestEntity = new StringBuilder()
								(0..entityLength).each { i ->
									requestEntity.append reader.read()	
								}
							}
							println "- got request: \n$request"
							println "- Connected: ${sock.connected} Closed: ${sock.closed}"
							output << "HTTP/1.1 200 OK\r\nContent-Type:text/plain\r\n" \
								+ "Content-Length:5\r\nConnection: Close\r\n\r\nHello\u0000"
							output.flush()
							println "- sent response!"
							output.close()
						}
					}
				}
				println "- Done normally"
			} finally {
				ss?.close()
				println "- Server closed."
			}
		}
		
		def http = new HTTPBuilder( 'http://localhost:11234', TEXT )
		http.headers = ['Content-Type':'text/xml',Accept:'text/xml']
		HttpConnectionParams.setSoTimeout( http.client.params, 10000 )
		
		println "= Client Sending request..."
		def response = http.get( path:'/one/two' )
		println "= Client got response"
		done = true
		
		assert request
		assert response
		// TODO validate headers
	}

}

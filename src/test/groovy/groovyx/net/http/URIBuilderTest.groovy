package groovyx.net.http

import org.junit.Test

/**
 * @author tnichols
 */
public class URIBuilderTest {
	
	@Test public void testPath() {
		def uri = new URIBuilder( 'http://localhost/p1/p2?a=1&b=2&c=3#frag' )
	
		uri.path = 'p2a'
		assert uri.toString() == 'http://localhost/p1/p2a?a=1&b=2&c=3#frag'
		
		uri.path = 'p2/p3/'
		assert uri.toString() == 'http://localhost/p1/p2/p3/?a=1&b=2&c=3#frag'

		uri.path = 'p4'
		assert uri.toString() == 'http://localhost/p1/p2/p3/p4?a=1&b=2&c=3#frag'

		uri.path = '../../p2b'
		assert uri.toString() == 'http://localhost/p1/p2b?a=1&b=2&c=3#frag'
	}
	
	@Test public void testParams() {
		def uri = new URIBuilder( 'http://localhost/p1/p2?a=1&b=2&c=3#frag' )
		assert uri.query.size() == 3
		assert uri.query.a == '1'
		assert uri.query.b == '2'
		assert uri.query.c == '3'
		
		uri.addQueryParam 'd', '4'
		assert uri.query.d == '4'
		
		uri.query = [z:0,y:9,x:8]
		println uri
		assert uri.toString() == 'http://localhost/p1/p2?z=0&y=9&x=8#frag'
	}

	@Test public void testMostEverythingElse() {
		def url = 'http://www.google.com:80/one/two?a=1#frag'		
		def uri = new URIBuilder( url )
		
		uri.scheme = 'https'
		assert uri.toString() == 'https://www.google.com:80/one/two?a=1#frag'
				
		uri.host = 'localhost'
		assert uri.toString() == 'https://localhost:80/one/two?a=1#frag'
		
		uri.port = 8080
		assert uri.toString() == 'https://localhost:8080/one/two?a=1#frag'

		uri.fragment = 'asdf2'
		assert uri.toString() == 'https://localhost:8080/one/two?a=1#asdf2'
	}
}

package groovyx.net.http

import org.junit.Test

/**
 * @author tnichols
 */
public class URIBuilderTest {
	
	@Test public void testConstructors() {
		def uri 
		try { uri = new URIBuilder( null ) }
		catch( ex ) { /* Expected exception */ }

		def string = 'http://google.com/search/q?one=1'
		uri = new URIBuilder( new URI( string ) )
		assert uri.toString() == string
		
		uri = new URIBuilder( new URL( string ) )
		assert uri.toString() == string
		
		uri = new URIBuilder( string )
		assert uri.toString() == string
		
		def uri2 = URIBuilder.convertToURI( uri )
		assert uri2 == uri.toURI()
	}
	
	@Test public void testCloneAndEquals() {
		def string = 'http://google.com/search/q?one=1'
		def uri = new URIBuilder( string )
		def u2 = uri.clone()
		assert u2 == uri
		assert u2.toString() == uri.toString()
	}
	
	@Test public void testTypecast() {
		def string = 'http://google.com/search/q?one=1'
		def uri = new URIBuilder( string )
		
		def u2 = uri as URI
		assert ( u2 instanceof URI )
		assert u2.toString() == uri.toString()

		u2 = uri as URL
		assert ( u2 instanceof URL )
		assert u2.toString() == uri.toString()

		u2 = uri as String
		assert ( u2 instanceof String )
		assert u2 == uri.toString()
		
		u2 = new URIBuilder( new URI(string) )
		assert u2 == uri
		assert u2.toString() == uri.toString()
		
		u2 = new URIBuilder( new URL(string) )
		assert u2 == uri
		assert u2.toString() == uri.toString()
	}
	
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
		
		def p = 'p5'
		uri.path = "/p4/$p"
		assert uri.toString() == 'http://localhost/p4/p5?a=1&b=2&c=3#frag'
	}
	
	@Test public void testPathEscaping() {
		def uri = new URIBuilder( 'http://localhost/' )
		uri.path = "/p1 p2"
		assert uri.toString() == 'http://localhost/p1%20p2'
		
		uri = new URIBuilder( 'http://localhost/p1?one=1#frag' )
		uri.path = "/p1 p2 p3"
		assert uri.toString() == 'http://localhost/p1%20p2%20p3?one=1#frag'
	}
	
	// When params are added to a path, does it goober up the escaped-then-unescaped path?
	@Test public void testPathEscaping2() {
		def uri = new URIBuilder( 'http://johannburkard.de/%22bla%22' )
		uri.query = [ what_is_this: 'i_dont_even' ]

		uri = new URIBuilder( 'http://codehaus.org/')
		uri.path = '"bla"'
		
		uri.fragment = 'what evs'
		assert uri.toString() == 'http://codehaus.org/%22bla%22#what%20evs'
		uri.query = [ a: 'b' ]
		assert uri.toString() == 'http://codehaus.org/%22bla%22?a=b#what%20evs'
		uri.port = 80
		uri.host = 'google.com'
		uri.scheme = 'http'
	}
	
	@Test public void testParams() {
		def uri = new URIBuilder( 'http://localhost/p1/p2?a=1&b=2&c=3#frag' )
		assert uri.query.size() == 3
		assert uri.query.a == '1'
		assert uri.query.b == '2'
		assert uri.query.c == '3'
		
		uri.addQueryParam 'd', '4'
		assert uri.query.d == '4'
		
		assert uri.hasQueryParam( "d" )
		
		uri.removeQueryParam "b"
		assert ! uri.hasQueryParam( "b" )
		assert uri.query.b == null
		uri.addQueryParam 'b', ''
		
		uri.addQueryParams( [ e : 5, f : 6, a : 7 ] )
		
		def query = uri.query
		assert query.containsKey( 'b' )
		assert query.size() == 6
		assert query == [a:['1','7'], c:'3', d:'4', b:null, e:'5', f:'6']

		uri.query = [z:0,y:9,x:8]
		assert uri.toString() == 'http://localhost/p1/p2?z=0&y=9&x=8#frag'
				
		uri.addQueryParams( z:1, y:2 )
		assert uri.toString() == 'http://localhost/p1/p2?z=0&y=9&x=8&z=1&y=2#frag'
				
		uri.addQueryParam 'y', 'blah'
		assert uri.toString() == 'http://localhost/p1/p2?z=0&y=9&x=8&z=1&y=2&y=blah#frag'

//		uri.addQueryParam '', 'asdf'
//		println uri // prints ...p2?=asdf... but apparently that is a valid URI...
				
		uri.query = null
		assert uri.toString() == 'http://localhost/p1/p2#frag'
		
		uri.query = [:]
		assert uri.toString() == 'http://localhost/p1/p2#frag'
		
		uri = new URIBuilder( 'http://localhost/p1/p2' )
		uri.addQueryParam 'z','1'
		assert uri.query.z == '1'
	}

	@Test public void testMostEverythingElse() {
		def url = 'http://www.google.com:80/one/two?a=1#frag'		
		def uri = new URIBuilder( url )
		
		uri.scheme = "https"
		assert uri.toString() == 'https://www.google.com:80/one/two?a=1#frag'
				
		uri.host = "localhost"
		assert uri.toString() == 'https://localhost:80/one/two?a=1#frag'
		
		uri.port = 8080
		assert uri.toString() == 'https://localhost:8080/one/two?a=1#frag'

		uri.fragment = 'asdf2'
		assert uri.toString() == 'https://localhost:8080/one/two?a=1#asdf2'
	}
	
	@Test public void testParamEncoding(){
		def uri = new URIBuilder( 'http://localhost:8080#test' )
		
		uri.query = [q:'a:b',z:'y&z']
		assert 'http://localhost:8080?q=a%3Ab&z=y%26z#test' == uri.toString()
		
		uri.query = [q:"war & peace"]
		assert "http://localhost:8080?q=war+%26+peace#test" == uri.toString()
						
		uri.query = ['a&b':'c+d=e']
		assert "http://localhost:8080?a%26b=c%2Bd%3De#test" == uri.toString() 
	}
	
	@Test public void testProperties() {
		// getQuery is already covered;
		def uri = new URIBuilder('http://localhost/')
		uri.setScheme( 'https' )
			.setUserInfo( 'bill' )
			.setHost( 'www.google.com' )
			.setPort( 88 )
			.setPath( 'test' )
			.setFragment( 'blah' )
			
		assert uri.toString() == 'https://bill@www.google.com:88/test#blah'
		
		assert uri.scheme == 'https'
		assert uri.userInfo == 'bill'
		assert uri.host == 'www.google.com'
		assert uri.port == 88
		assert uri.path == '/test'
		assert uri.fragment == 'blah'
	}
}

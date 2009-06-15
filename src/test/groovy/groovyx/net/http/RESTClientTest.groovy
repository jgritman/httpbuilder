package groovyx.net.http

import org.junit.Testimport org.junit.Before
import junit.framework.Assertimport groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.*

/**
 * @author tnichols
 *
 */
public class RESTClientTest {

	def twitter = null
	static postID = null
	def userID = System.getProperty('twitter.user')
	
	@Before public void setUp() {
		twitter = new RESTClient( 'https://twitter.com/statuses/' )
		twitter.auth.basic userID, System.getProperty('twitter.passwd')
		twitter.client.params.setBooleanParameter 'http.protocol.expect-continue', false
		twitter.contentType = ContentType.JSON
	}
	
	@Test public void testConstructors() {
		twitter = new RESTClient()
		assert twitter.contentType == ContentType.ANY
		
		twitter = new RESTClient( 'http://www.google.com', ContentType.XML )
		assert twitter.contentType == ContentType.XML
	}
	
	@Test public void testHead() {
		try { // expect an exception from a 404 response:
			twitter.head path : 'public_timeline'
			assert false, 'Expected exception'
		}
		// test the exception class:
		catch( ex ) { assert ex.response.status == 404 }
		
		assert twitter.head( path : 'public_timeline.json' ).status == 200
	}
	
	@Test public void testGet() {
		// testing w/ content-type other than default:
		/* Note also that Twitter doesn't really care about the "Accept" header
		   anyway, it wants you to put it in the URL, i.e. something.xml or
		   something.json.  But we're still passing the content-type so that 
		   the parser knows how it should _attempt_ to parse the response.  */
		def resp = twitter.get( path : 'friends_timeline.xml', contentType:XML )
		assert resp.status == 200
		assert resp.data?.status.size() > 0
		
		resp = twitter.get( path : 'friends_timeline.json' )
		assert resp.status == 200
		assert resp.headers.Server == "hi"
		assert resp.headers.Server == resp.headers['Server'].value
		assert resp.contentType == JSON.toString()
		assert ( resp.data instanceof net.sf.json.JSON )
		assert resp.data.status.size() > 0
		
		// Now, set the default content-type back to "*/*" which will cause 
		// the client to parse based solely on the response content-type header.
		twitter.contentType = '*/*'  //ANY
		resp = twitter.get( path : 'friends_timeline.xml' )
		assert resp.status == 200
		assert ( resp.data instanceof GPathResult )
		assert resp.data.status.size() > 0
	}
	
	@Test public void testPost() {
		def msg = "RESTClient unit test was run on ${new Date()}"
			
		def resp = twitter.post( 
				path : 'update.xml', 
				contentType : XML, 
				body : [ status:msg, source:'httpbuilder' ],
				requestContentType : URLENC )

		assert resp.status == 200
		assert resp.headers.Status
		assert resp.data instanceof GPathResult // parsed using XmlSlurper 
		assert resp.data.text == msg
		assert resp.data.user.screen_name == userID

		RESTClientTest.postID = resp.data.id.text()
		println "Updated post; ID: ${postID}"
	}
	
	@Test public void testPut() {
		try {
			twitter.put( path : 'update.xml', 
					contentType : XML, 
					body : [ status:'test', source:'httpbuilder' ],
					requestContentType : URLENC )
					
		} catch ( HttpResponseException ex ) {
			assert ex.response.headers
			assert ex.response.headers.Status =~ '400' //'405'
		}		
	}
	
	@Test public void testDelete() {
		// delete the test message.
		if ( ! postID ) throw new IllegalStateException( "No post ID from testPost()" )
		println "Deleting post ID : $postID"
		def resp = twitter.delete( path : "destroy/${postID}.json" )
		assert resp.status == 200
		assert resp.data.id.toString() == postID
		println "Test tweet ID ${resp.data.id} was deleted."
	}
	
	@Test public void testOptions() {
		// get a message ID then test which ways I can delete it:
		def resp = twitter.get( uri: 'http://twitter.com/statuses/user_timeline/httpbuilder.json' )
		
		def id = resp.data[0].id
		assert id
		
		// This does not seem to be supported by the Twitter API..
/*		resp = twitter.options( path : "destroy/${id}.json" )
		println "OPTIONS response : ${resp.headers.Allow}"
		assert resp.headers.Allow
		*/
	}
}

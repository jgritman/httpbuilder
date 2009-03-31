package groovyx.net.http

import static groovyx.net.http.Method.*
import static groovyx.net.http.ContentType.*
import org.junit.Test
import java.lang.AssertionErrorimport java.io.Readerimport groovy.util.XmlSlurperimport groovy.util.slurpersupport.GPathResultimport org.apache.http.client.HttpResponseExceptionimport java.io.ByteArrayOutputStream
class HTTPBuilderTest {
	
	def twitter = [user: System.getProperty('twitter.user'),
	               passwd: System.getProperty('twitter.passwd') ]
	
	/**
	 * This method will parse the content based on the response content-type
	 */
	@Test public void testGET() {
		def http = new HTTPBuilder('http://www.google.com')
		http.get( path:'/search', query:[q:'Groovy'] ) { resp, html ->
			println "response status: ${resp.statusLine}"
			
			assert html
			assert html.HEAD.size() == 1
			assert html.HEAD.TITLE.size() == 1
			println "Title: ${html.HEAD.TITLE.text()}"
			assert html.BODY.size() == 1
		}
	}
	
	@Test public void testDefaultSuccessHandler() {
		def http = new HTTPBuilder('http://www.google.com')
		def html = http.request( GET ) {
			uri.path = '/search'
			uri.query = [q:'Groovy']
		}
		assert html instanceof GPathResult
		assert html.HEAD.size() == 1
		assert html.BODY.size() == 1

		// short form where GET takes no response handler.
		html = http.get( path:'/search', query:[q:'Groovy'] )
		assert html instanceof GPathResult
		assert html.HEAD.size() == 1
		assert html.BODY.size() == 1
	}
	
	@Test public void testSetHeaders() {
		def http = new HTTPBuilder('http://www.google.com')
		def val = '1'
		def v2 = 'two'
		def h3 = 'three'
		def h4 = 'four'
		http.headers = [one:"v$val", "$v2" : 2]
		http.headers.three = 'not Three'
		http.headers."$h3" = 'three'
		
		def request
		def html = http.request( GET ) { client, req ->
			assert headers.one == 'v1'
			assert headers.two == '2'
			assert headers.three == 'three'
			headers."$h4" = "$val"
			request = req
		}
		
		assert html
		def headers = request.allHeaders
		assert headers.find { it.name == 'one' && it.value == 'v1' }
		assert headers.find { it.name == 'two' && it.value == '2' }
		assert headers.find { it.name == 'three' && it.value == 'three' }
		assert headers.find { it.name == 'four' && it.value == '1' }
	}
	
	/* This tests a potential bug -- the reader is being accessed after the 
	 * request/response sequence has finished and response.consumeContent() 
	 * has already been called internally.  In practice, it appears that the 
	 * response data is being buffered, probably for any non-chunked responses.
	 * A warning should probably be added, however, that the default response 
	 * handler will not work well with a chunked response if it is parsed as 
	 * TEXT or BINARY.
	 */
	@Test public void testReaderWithDefaultResponseHandler() {
		def http = new HTTPBuilder('http://www.google.com')
		
		def reader = http.get( contentType:TEXT )
		
		assert reader instanceof Reader
		def out = new ByteArrayOutputStream()
		out << reader
		assert out.toString().length() > 0
		assert out.toString().endsWith( '</html>' )
		//println out.toString()
	}
	
	@Test public void testDefaultFailureHandler() {
		def http = new HTTPBuilder('http://www.google.com')

		try {
			http.get( path:'/adsasf/kjsslkd' ) {
				assert false 
			}
		}
		catch( HttpResponseException ex ) {
			assert ex.statusCode == 404
		}
		
	}	
	
	/**
	 * This method is similar to the above, but it will will parse the content 
	 * based on the given content-type, i.e. TEXT (text/plain).  
	 */
	@Test public void testReader() {
		def http = new HTTPBuilder('http://w3c.org')
		http.get( uri:'http://validator.w3.org/about.html', 
				  contentType: TEXT, headers: [Accept:'*/*'] ) { resp, reader ->
			println "response status: ${resp.statusLine}"
			
			assert reader instanceof Reader
			
			// we'll validate the reader by passing it to an XmlSlurper manually.
			def parsedData = new XmlSlurper().parse(reader)
			assert parsedData.children().size() > 0
		}
	}
	
	/* REST testing with Twitter!
	 * Tests POST with XML response, and DELETE with a JSON response.
	 */

	@Test public void testPOSTwithXML() {
		def http = new HTTPBuilder('http://twitter.com/statuses/')
		
		http.auth.basic twitter.user, twitter.passwd
		/* twitter doesn't like the Expect: 100 header because it would have
		   replied with a 401 error --- but since "Expect: 100" is there, it 
		   will actually reply with a 417 (Expectation failed) instead!  So
		   the easiest solution is to remove the Expect header.  You might 
		   also be able to add an "Expect: 401?" 
		   
		   This could also be solved by doing a GET request or the like first, 
		   which will cause the client to encounter the 401.  Another option 
		   would be 'preemptive auth' but that would take some more code to 
		   implement.  */
		http.client.params.setBooleanParameter 'http.protocol.expect-continue', false
		
		def msg = "HTTPBuilder unit test was run on ${new Date()}"
		
		def postID = http.request( POST, XML ) { req ->
			uri.path = 'update.xml'
			send URLENC, [status:msg,source:'httpbuilder']
			
			//req.params.setBooleanParameter 'http.protocol.expect-continue', false
			
			 response.success = { resp, xml ->
				println "Tweet response status: ${resp.statusLine}"
				assert resp.statusLine.statusCode == 200
				assert xml instanceof GPathResult 
				
				assert xml.text == msg
				assert xml.user.screen_name == twitter.user
				return xml.id
			}
		}
		
		// delete the test message.
		http.request( DELETE, JSON ) { req ->
			uri.path = "destroy/${postID}.json"
			
			response.success = { resp, json ->
				assert json.id != null
				assert resp.statusLine.statusCode == 200
				println "Test tweet ID ${json.id} was deleted."
			}
		}
	}
	
	@Test public void testHeadMethod() {
		def http = new HTTPBuilder('http://twitter.com/statuses/')
		
		http.auth.basic twitter.user, twitter.passwd
		
		http.request( HEAD, XML ) {
			uri.path = 'friends_timeline.xml' 
			
//			response.'401' = { }
			
			response.success = { resp ->
				assert resp.getFirstHeader('Status').value == "200 OK"
			}
		}
		
//		http.request( HEAD, XML ) { 
//			uri.path = 'friends_timeline.xml' 
//			response.success = { resp ->
//				assert resp.statusLine.statusCode == 200
//			}
//		}
	}
	
	@Test public void testRequestAndDefaultResponseHandlers() {
		def http = new HTTPBuilder()

//		 default response handlers.
		http.handler.'401' = { resp ->
			println 'access denied'
		}

		http.handler.failure = { resp ->
			println "Unexpected failure: ${resp.statusLine}"
		}

//		 optional default URL for all actions:
		http.uri = 'http://www.google.com' 

		http.request(GET,TEXT) { req ->
			response.success = { resp, stream ->
				println 'my response handler!'
				assert resp.statusLine.statusCode == 200
				println resp.statusLine
				//System.out << stream // print response stream
			}
		}	
	}
	
	/**
	 * Test a response handler that is assigned within a request config closure:
	 */
	@Test public void test404() {
		new HTTPBuilder().request('http://www.google.com',GET,TEXT) {
			uri.path = '/asdfg/asasdfs' // should produce 404
			response.'404' = {
				println 'got expected 404!'
			}
			response.success = {
				assert false // should not have succeeded
			}
		}
	}
	
	/* http://googlesystem.blogspot.com/2008/04/google-search-rest-api.html
	 * http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=Earth%20Day
	 */
	@Test public void testJSON() {
		
		def builder = new HTTPBuilder()
		
//		builder.parser.'text/javascript' = builder.parsers."$JSON"
		
		builder.request('http://ajax.googleapis.com',GET,JSON) {
			uri.path = '/ajax/services/search/web'
			uri.query = [ v:'1.0', q: 'Calvin and Hobbes' ]
			//UA header required to get Google to GZIP response:
			headers.'User-Agent' = "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.0.4) Gecko/2008111319 Ubuntu/8.10 (intrepid) Firefox/3.0.4"
			response.success = { resp, json ->
				assert resp.entity.contentEncoding.value == 'gzip'
				assert json.size() == 3
				//println json.responseData
				println "Query response: "
				json.responseData.results.each { 
					println "  ${it.titleNoFormatting} : ${it.visibleUrl}"
				}
				null
			}
		}
	}
	
	@Test public void testAuth() {
		def http = new HTTPBuilder( 'http://test.webdav.org' )
		
		/* The path issues a 404, but it does an auth challenge first. */
		http.handler.'404' = { println 'Auth successful' }
		
		http.request( GET, HTML ) {
			uri.path = '/auth-digest/'
			response.failure = { "expected failure" }
			response.success = {
				throw new AssertionError("request should have failed.")
			}
		}
		
		http.auth.basic( 'user2', 'user2' )

		http.request( GET, HTML ) {
			uri.path = '/auth-digest/'
		}
		
		http.request( GET, HTML ) {
			uri.path = '/auth-basic/'
		}
	}
}
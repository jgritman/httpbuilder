package groovyx.net.http

import static groovyx.net.http.Method.*
import static groovyx.net.http.ContentType.*
import org.junit.Test
import java.lang.AssertionErrorimport java.io.Readerimport groovy.util.XmlSlurperimport groovy.util.slurpersupport.GPathResult
class HTTPBuilderTest {
	
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
	
	/**
	 * This method is similar to the above, but it will will parse the content 
	 * based on the given content-type, i.e. TEXT (text/plain).  
	 */
	@Test public void testReader() {
		def http = new HTTPBuilder('http://w3c.org')
		http.get( url:'http://validator.w3.org/about.html', 
				  contentType: TEXT ) { resp, reader ->
			println "response status: ${resp.statusLine}"
			
			assert reader instanceof Reader
			
			// we'll validate the reader by passing it to an XmlSlurper manually.
			def parsedData = new XmlSlurper().parse(reader)
			assert parsedData.children().size() > 0
		}
	}
	
	/* REST testing with Twitter!
	 */
	@Test public void testPOSTwithXML() {
		def http = new HTTPBuilder('http://twitter.com/statuses/')
		
		http.auth.basic( 'httpbuilder', 'c0deH@us!' )
		
		def msg = "HTTPBuilder unit test was run on ${new Date()}"
		
		http.request( POST, XML ) { req ->
			url.path = 'update.xml'
			send URLENC, [status:msg]
			
			/* twitter doesn't like the Expect: 100 header because it would have
			   replied with a 401 error --- but since "Expect: 100" is there, it 
			   will actually reply with a 417 (Expectation failed) instead!  So
			   the easiest solution is to remove the Expect header.  You might 
			   also be able to add an "Expect: 401?" 
			   
			   This could also be solved by doing a GET request or the like first, 
			   which will cause the client to encounter the 401.  Another option 
			   would be 'preemptive auth' but that would take some more code to 
			   implement.  */
			req.params.setBooleanParameter 'http.protocol.expect-continue', false
			
			 response.success = { resp, xml ->
				println "response status: ${resp.statusLine}"
				assert resp.statusLine.statusCode == 200
				assert xml instanceof GPathResult 
				
				assert xml.text == msg
				assert xml.user.name == 'httpbuilder'
			}
		}
	}
	
	@Test public void testHeadMethod() {
		def http = new HTTPBuilder('http://twitter.com/statuses/')
		
		http.auth.basic( 'httpbuilder', 'c0deH@us!' )
		
		http.request( HEAD, XML ) { // this will result in a 401 status code
			url.path = 'friends_timeline.xml' 
			
//			response.'401' = { }
			
			response.success = { resp ->
				assert resp.getFirstHeader('Status').value == "200 OK"
			}
		}
		
//		http.request( HEAD, XML ) { 
//			url.path = 'friends_timeline.xml' 
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
		http.URL = 'http://www.google.com' 

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
			url.path = '/asdfg/asasdfs' // should produce 404
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
			url.path = '/ajax/services/search/web'
			url.query = [ v:'1.0', q: 'Calvin and Hobbes' ]
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
			url.path = '/auth-digest/'
			response.failure = { "expected failure" }
			response.success = {
				throw new AssertionError("request should have failed.")
			}
		}
		
		http.auth.basic( 'user2', 'user2' )

		http.request( GET, HTML ) {
			url.path = '/auth-digest/'
		}
		
		http.request( GET, HTML ) {
			url.path = '/auth-basic/'
		}
	}
}
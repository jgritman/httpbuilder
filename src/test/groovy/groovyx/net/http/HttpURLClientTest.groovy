package groovyx.net.http

import org.junit.Test
import java.lang.AssertionErrorimport java.io.Readerimport groovy.util.XmlSlurperimport groovy.util.slurpersupport.GPathResultimport org.apache.http.client.HttpResponseExceptionimport java.io.ByteArrayOutputStream
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
class HttpURLClientTest {
	
	def twitter = [user: System.getProperty('twitter.user'),
	               passwd: System.getProperty('twitter.passwd') ]
	
	/**
	 * This method will parse the content based on the response content-type
	 */
	@Test public void testGET() {
		def http = new HttpURLClient(url:'http://www.google.com')
		def resp = http.request( path:'/search', query:[q:'HTTPBuilders'],
				headers:['User-Agent':'Firefox'] )
		
		println "response status: ${resp.statusLine}"
		
		def html = resp.data
		assert html
		assert html.HEAD.size() == 1
		assert html.HEAD.TITLE.size() == 1
		println "Title: ${html.HEAD.TITLE.text()}"
		assert html.BODY.size() == 1
	}
	
	@Test public void testSetHeaders() {
		def http = new HttpURLClient(url:'http://www.google.com')
		def val = '1'
		def v2 = 'two'
		def h3 = 'three'
		def h4 = 'four'
		http.headers = [one:"v$val", "$v2" : 2]
		http.headers.three = 'not Three'
		http.headers."$h3" = 'three'
		
		def request
		def resp = http.request( headers:[one:'v1',two:'2',three:'three',"$h4":"$val"] )
		
		assert resp
/*		def headers = request.allHeaders
		assert headers.find { it.name == 'one' && it.value == 'v1' }
		assert headers.find { it.name == 'two' && it.value == '2' }
		assert headers.find { it.name == 'three' && it.value == 'three' }
		assert headers.find { it.name == 'four' && it.value == '1' }
*/	}

	
	@Test public void testDefaultFailureHandler() {
		def http = new HttpURLClient(url:'http://www.google.com')

		try {
			def resp = http.request( path:'/adsasf/kjsslkd' )
		}
		catch( HttpResponseException ex ) {
			assert ex.statusCode == 404
		}
		
	}	
	
	/**
	 * This method is similar to testGET, but it will will parse the content 
	 * based on the given content-type, i.e. TEXT (text/plain).  
	 */
	@Test public void testReader() {
		def http = new HttpURLClient()
		def resp = http.request( url:'http://validator.w3.org/about.html', 
				  contentType: TEXT, headers: [Accept:'*/*'] )
		
		println "response status: ${resp.statusLine}"
		
		assert resp.data instanceof Reader
			
		// we'll validate the reader by passing it to an XmlSlurper manually.
		def parsedData = new XmlSlurper().parse(resp.data)
		assert parsedData.children().size() > 0
	}
	
	/* REST testing with Twitter!
	 * Tests POST with XML response, and DELETE with a JSON response.
	 */

	@Test public void testPOSTwithXML() {
		def http = new HttpURLClient(url:'http://twitter.com/statuses/')
		
		http.setBasicAuth twitter.user, twitter.passwd
		
		def msg = "HTTPBuilder unit test was run on ${new Date()}"
		
		def resp = http.request( method:POST, contentType:XML,
				path:'update.xml',
				requestContentType:URLENC, 
				body:[status:msg,source:'httpbuilder'] )
			
		println "Tweet response status: ${resp.statusLine}"
		assert resp.statusLine.statusCode == 200
		def xml = resp.data
		assert xml instanceof GPathResult 
				
		assert xml.text == msg
		assert xml.user.screen_name == twitter.user
		def postID = xml.id
		
		// delete the test message.
		resp = http.request( method:DELETE, contentType:JSON,
			path : "destroy/${postID}.json" )
			
		def json = resp.data
		assert json.id != null
		assert resp.statusLine.statusCode == 200
		println "Test tweet ID ${json.id} was deleted."
	}
	
	@Test public void testHeadMethod() {
		def http = new HttpURLClient(url:'http://twitter.com/statuses/')
		
		http.setBasicAuth twitter.user, twitter.passwd
		
		def resp = http.request( method:HEAD, contentType:XML, path : 'friends_timeline.xml' ) 
			
		assert resp.headers.Status.value == "200 OK"		
	}
		
	/* http://googlesystem.blogspot.com/2008/04/google-search-rest-api.html
	 * http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=Earth%20Day
	 */
	@Test public void testJSON() {
		
		def http = new HttpURLClient()
		
		def resp = http.request( url:'http://ajax.googleapis.com',
				method:GET, contentType:JSON ,
			path : '/ajax/services/search/web',
			query : [ v:'1.0', q: 'Calvin and Hobbes' ],
			//UA header required to get Google to GZIP response:
			headers:['User-Agent' : "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.0.4) Gecko/2008111319 Ubuntu/8.10 (intrepid) Firefox/3.0.4"] )

//		assert resp.entity.contentEncoding.value == 'gzip'
		def json = resp.data
		assert json.size() == 3
//		println json.responseData
		println "Query response: "
		json.responseData.results.each { 
			println "  ${it.titleNoFormatting} : ${it.visibleUrl}"
		}
	}	
}
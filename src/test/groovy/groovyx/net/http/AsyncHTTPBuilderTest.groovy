/*
 * Copyright 2003-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You are receiving this code free of charge, which represents many hours of
 * effort from other individuals and corporations.  As a responsible member 
 * of the community, you are asked (but not required) to donate any 
 * enhancements or improvements back to the community under a similar open 
 * source license.  Thank you. -TMN
 */
package groovyx.net.http

import org.junit.Test
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import java.util.concurrent.ExecutionExceptionimport java.net.SocketTimeoutException
/**
 * @author tnichols
 */
public class AsyncHTTPBuilderTest {

	@Test public void testAsyncRequests() {
		def http = new AsyncHTTPBuilder( poolSize : 4,
						uri : 'http://hc.apache.org',
						contentType : ContentType.HTML )
		
		def done = []
		
		done << http.get(path:'/') { resp, html ->
			println ' response 1'
			true
		}

		done << http.get(path:'/httpcomponents-client/httpclient/') { resp, html ->
			println ' response 2'
			true
		}

		done << http.get(path:'/httpcomponents-core/') { resp, html ->
			println ' response 3'
			true
		}

		done << http.get(uri:'http://svn.apache.org/') { resp, html ->
			println ' response 4'
			true
		}
		
		println done.size()
		
		def timeout = 30000
		def time = 0
		while ( true ) {
			if ( done.every{ it.done ? it.get() : 0 } ) break
			print '.'
			Thread.sleep 2000
			time += 2000
			if ( time > timeout ) assert false
		}
		http.shutdown()
		println 'done.'
	}

	@Test public void testDefaultConstructor() {
		def http = new AsyncHTTPBuilder()
		def resp = http.get( uri:'http://ajax.googleapis.com', 
					path : '/ajax/services/search/web',
					params : [ v:'1.0', q: 'Calvin and Hobbes' ],
					contentType: JSON )
					
		while ( ! resp.done  ) Thread.sleep 2000
		assert resp.get().size()
		assert resp.get().responseData.results
		http.shutdown()
	}

	@Test public void testPostAndDelete() {
		def http = new AsyncHTTPBuilder(uri:'http://twitter.com/statuses/')
		
		http.auth.basic System.getProperty('twitter.user'),
		               System.getProperty('twitter.passwd')

		http.client.params.setBooleanParameter 'http.protocol.expect-continue', false
		
		def msg = "AsyncHTTPBuilder unit test was run on ${new Date()}"
		
		def resp = http.post( contentType:XML, path : 'update.xml',
			body:[status:msg,source:'httpbuilder'] )
			
		while ( ! resp.done  ) Thread.sleep 2000 
		def postID = resp.get().id.toInteger()
		assert postID
		
		// delete the test message.
		resp = http.request( DELETE, JSON ) { req ->
			uri.path = "destroy/${postID}.json"
			
			response.success = { resp2, json ->
				assert json.id != null
				assert resp2.statusLine.statusCode == 200
				println "Test tweet ID ${json.id} was deleted."
				return json
			}
		}
		
		while ( ! resp.done  ) Thread.sleep( 2000 )
		assert resp.get().id == postID
		http.shutdown()
	}
	
	
	@Test public void testTimeout() {
		def http = new AsyncHTTPBuilder( uri:'http://ajax.googleapis.com', 
				contentType: JSON, timeout:2 ) // 2ms to force timeout

		assert http.timeout == 2 
		
		def resp = http.get( path : '/ajax/services/search/web', 
				params : [ v:'1.0', q: 'HTTPBuilder' ] )

		Thread.sleep 100
		try {
			resp.get()
			assert false
		}
		catch ( ExecutionException ex ) {
			assert ex.cause.getClass() == SocketTimeoutException
		}
	}
	
	@Test public void testPoolsizeAndQueueing() {
		def http = new AsyncHTTPBuilder( poolsize : 1 ,
				uri : 'http://ajax.googleapis.com/ajax/services/search/web' )
		
		def responses = []
		/* With one thread in the pool, responses will be sequential but should 
		 * queue up w/o being rejected. */
		responses << http.get( params : [q:'Groovy', v:'1.0'] )
		responses << http.get( params : [q:'Ruby', v:'1.0'] )
		responses << http.get( params : [q:'Scala', v:'1.0'] )
		
		def timeout = 60000
		def time = 0
		while ( true ) {
			if ( responses.every{ it.done ? it.get() : 0 } ) break
			print '.'
			Thread.sleep 2000
			time += 2000
			if ( time > timeout ) assert false
		}
		println()
		http.shutdown()
	}
}
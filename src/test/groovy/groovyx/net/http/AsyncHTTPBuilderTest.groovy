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

/**
 * @author tnichols
 */
public class AsyncHTTPBuilderTest {

	@Test public void testAsyncRequests() {
		def http = new AsyncHTTPBuilder(poolSize:4,
						url:'http://hc.apache.org',
						contentType:ContentType.HTML )
		
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

		done << http.get(url:'http://svn.apache.org/') { resp, html ->
			println ' response 4'
			true
		}
		
		println done.size()
		
		def timeout = 20000
		def time = 0
		while ( true ) {
			if ( done.every{ it.done ? it.get() : 0 } ) break;
			println '  waiting...'
			Thread.sleep(2000)
			time += 2000
			if ( time > timeout ) assert false
		}
		println 'done.'
	}
}
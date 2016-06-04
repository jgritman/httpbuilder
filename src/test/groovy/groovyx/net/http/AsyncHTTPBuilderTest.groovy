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
package groovyx.net.http;

import spock.lang.*;
import static groovyx.net.http.ContentType.*;
import static groovyx.net.http.Method.*;
import java.util.concurrent.ExecutionException;
import org.apache.http.conn.ConnectTimeoutException;
import java.util.concurrent.TimeUnit;

class AsyncHTTPBuilderTest extends Specification {

    final static Closure returnTrue = { resp, html -> true; };
    
    def "Asynch Requests"() {
        setup:
        def http = new AsyncHTTPBuilder(poolSize: 4, uri: 'http://hc.apache.org',
                                        contentType: ContentType.HTML);
        
        def futures = [ http.get(path:'/', returnTrue),
                        http.get(path:'/httpcomponents-client-ga/', returnTrue),
                        http.get(path:'/httpcomponents-core-dev/', returnTrue),
                        http.get(uri:'http://svn.apache.org/', returnTrue) ];

        expect:
        futures.every { req -> req.get(30L, TimeUnit.SECONDS); }

        cleanup:
        http.shutdown()
    }

    @Ignore
    def "Default Constructor"() {
        def http = new AsyncHTTPBuilder()
        def resp = http.get( uri:'http://ajax.googleapis.com',
                    path : '/ajax/services/search/web',
                    query : [ v:'1.0', q: 'Calvin and Hobbes' ],
                    contentType: JSON )

        while ( ! resp.done  ) Thread.sleep 2000
        assert resp.get().size()
        assert resp.get().responseData.results
        http.shutdown()
    }

    @Ignore
    def "Post And Delete"() {
        def http = new AsyncHTTPBuilder(uri:'https://api.twitter.com/1.1/statuses/')

        http.auth.oauth System.getProperty('twitter.oauth.consumerKey'),
                System.getProperty('twitter.oauth.consumerSecret'),
                System.getProperty('twitter.oauth.accessToken'),
                System.getProperty('twitter.oauth.secretToken')

        http.client.params.setBooleanParameter 'http.protocol.expect-continue', false

        def msg = "AsyncHTTPBuilder unit test was run on ${new Date()}"

        def resp = http.post(path : 'update.json',
            body:[status:msg,source:'httpbuilder1'] )

        while ( ! resp.done  ) Thread.sleep 2000
        def postID = resp.get().id
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


    def "Timeout"() {
        when:
        def http = new AsyncHTTPBuilder(uri:'http://netflix.com',
                                        contentType: HTML, timeout: 2) // 2ms to force timeout
        then:
        http.timeout == 2

        when:
        def resp = http.get(path: '/ajax/services/search/web',
                            query: [ v:'1.0', q: 'HTTPBuilder' ]);
        sleep(100L);
        resp.get();

        then:
        def ex = thrown(ExecutionException);
        ex.cause.getClass() == ConnectTimeoutException;

        cleanup:
        http.shutdown();
    }

    def "Pool Size And Queueing"() {
        setup:
        long timeout = 60L;
        def http = new AsyncHTTPBuilder(poolSize: 1 ,
                                        uri: 'http://ajax.googleapis.com/ajax/services/search/web');

        when:
        def futures = [ http.get(query: [q:'Groovy', v:'1.0'], returnTrue),
                        http.get(query: [q:'Ruby', v:'1.0'], returnTrue),
                        http.get(query: [q:'Scala', v:'1.0'], returnTrue) ];

        then:
        futures.every { resp -> resp.get(60L, TimeUnit.SECONDS); };

        cleanup:
        http.shutdown()
    }

    def "Invalid Named Arg"() {
        when:
        def http = new AsyncHTTPBuilder(poolsize: 1, //should be poolSize (why are we testing this?)
                                        uri: 'http://ajax.googleapis.com/ajax/services/search/web');

        then:
        thrown(IllegalArgumentException);
    }
}

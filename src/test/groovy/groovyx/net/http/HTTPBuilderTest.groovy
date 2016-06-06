package groovyx.net.http

import groovy.transform.CompileStatic
import groovy.util.slurpersupport.GPathResult
import org.apache.http.client.HttpResponseException
import spock.lang.*;

import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*
import static groovyx.net.http.HTTPBuilder.get;

class HTTPBuilderTest extends Specification {

    def twitter = [ user: System.getProperty('twitter.user'),
                    consumerKey: System.getProperty('twitter.oauth.consumerKey'),
                    consumerSecret: System.getProperty('twitter.oauth.consumerSecret'),
                    accessToken: System.getProperty('twitter.oauth.accessToken'),
                    secretToken: System.getProperty('twitter.oauth.secretToken') ];
    
    def "GET"() {
        when:
        def http = new HTTPBuilder('http://www.google.com')

        then:
        http.get( path:'/search', query:[q:'Groovy'],
                  headers:['User-Agent':"Firefox"] ) { resp, html ->
                      println "response status: ${resp.statusLine}"
                      println "Content Type: ${resp.headers.'Content-Type'}"
                      
                      return (html &&
                              html.HEAD.size() == 1 &&
                              html.HEAD.TITLE.size() == 1 &&
                              html.BODY.size() == 1);
        }
    }

    @CompileStatic
    def "Get Delegates To"() {
        setup:
        def html = get('http://www.google.com') {
            headers = ['User-Agent':"Firefox"]
            uri.path = '/search'
            uri.query = [q:'Groovy']
        };

        expect:
        html;
    }

    def "Default Success Handler"() {
        setup:
        def http = new HTTPBuilder('http://www.google.com')

        when:
        def html = http.request( GET ) {
            headers = ['User-Agent':"Firefox"]
            uri.path = '/search'
            uri.query = [q:'Groovy']
        }

        then:
        html instanceof GPathResult
        html.HEAD.size() == 1
        html.BODY.size() == 1

        when:
        html = http.get(path:'/search', query:[q:'Groovy'])

        then:
        html instanceof GPathResult
        html.HEAD.size() == 1
        html.BODY.size() == 1
    }

    def "Set Headers"() {
        setup:
        def http = new HTTPBuilder('http://www.google.com')
        def val = '1'
        def v2 = 'two'
        def h3 = 'three'
        def h4 = 'four'
        http.headers = [one:"v$val", "$v2" : 2]
        http.headers.three = 'not Three'
        http.headers."$h3" = 'three'

        when:
        def request
        def ret = http.request( GET ) { req ->
            headers."$h4" = "$val"
            request = req
            return (headers.one == 'v1' &&
                    headers.two == '2' &&
                    headers.three == 'three');
        }
        def headers = request.allHeaders
        
        then:
        ret;
        headers.find { it.name == 'one' && it.value == 'v1' };
        headers.find { it.name == 'two' && it.value == '2' };
        headers.find { it.name == 'three' && it.value == 'three' };
        headers.find { it.name == 'four' && it.value == '1' };
    }

    /* This tests a potential bug -- the reader is being accessed after the
     * request/response sequence has finished and response.consumeContent()
     * has already been called internally.  In practice, it appears that the
     * response data is being buffered, probably for any non-chunked responses.
     * A warning should probably be added, however, that the default response
     * handler will not work well with a chunked response if it is parsed as
     * TEXT or BINARY.
     */
    def "Reader With Default Response Handler"() {
        setup:
        def http = new HTTPBuilder('http://www.google.com');

        when:
        def reader = http.get(contentType: TEXT)
        def contents = reader.text;
        
        then:
        reader instanceof Reader
        contents.length() > 0;
        contents.trim().endsWith('</html>');
    }

    def "Default Failure Handler"() {
        setup:
        def http = new HTTPBuilder('http://www.google.com')

        when:
        http.get(path:'/adsasf/kjsslkd');

        then:
        def ex = thrown(HttpResponseException);
        ex.statusCode == 404
        ex.response.status == 404
        ex.response.headers
    }

    /**
     * This method is similar to the above, but it will will parse the content
     * based on the given content-type, i.e. TEXT (text/plain).
     */
    def "Reader"() {
        setup:
        def http = new HTTPBuilder('http://examples.oreilly.com');
        def resolver = ParserRegistry.catalogResolver;
        
        when:
        def result = http.get(uri: 'http://examples.oreilly.com/9780596002527/examples/first.xml',
                              contentType: TEXT, headers: [Accept:'*/*']) {
            resp, reader ->
                def returnedHeaders = [:];
                resp.headers.each { returnedHeaders[it.name] = it.value; };
                return [ headers: returnedHeaders, reader: reader,
                         parsedData: new XmlSlurper(entityResolver: resolver).parse(reader) ]; };

        then:
        result.headers.size() > 1;
        result.reader instanceof Reader;
        result.parsedData.children().size() > 0;
    }

    /* REST testing with Twitter!
     * Tests POST with JSON response, and DELETE with a JSON response.
     */

    @Ignore
    def "POST"() {
        def http = new HTTPBuilder('https://api.twitter.com/1.1/statuses/')

        http.auth.oauth twitter.consumerKey, twitter.consumerSecret,
                twitter.accessToken, twitter.secretToken

        def msg = "HTTPBuilder unit test was run on ${new Date()}"

        def postID = http.request( POST, JSON ) { req ->
            uri.path = 'update.json'
            send URLENC, [status:msg,source:'httpbuilder']

             response.success = { resp, json ->
                println "Tweet response status: ${resp.statusLine}"
                assert resp.statusLine.statusCode == 200
                println "Content Length: ${resp.headers['Content-Length']?.value}"

                assert json.text == msg
                assert json.user.screen_name == twitter.user
                return json.id
            }
        }

        // delete the test message.
        Thread.sleep 5000
        http.request( DELETE, JSON ) { req ->
            uri.path = "destroy/${postID}.json"

            response.success = { resp, json ->
                assert json.id != null
                assert resp.statusLine.statusCode == 200
                println "Test tweet ID ${json.id} was deleted."
            }
        }
    }

    @Ignore // twitter is returning a 401 for unknown reasons here
    def "Plain URL Enc"() {
        def http = new HTTPBuilder('https://api.twitter.com/1.1/statuses/')

        http.auth.oauth twitter.consumerKey, twitter.consumerSecret,
                twitter.accessToken, twitter.secretToken

        def msg = "HTTPBuilder's second unit test was run on ${new Date()}"

        def resp = http.post( contentType: JSON, path:'update.json',
                body:"status=$msg&source=httpbuilder" )

        def postID = resp.id.text()
        assert postID

        // delete the test message.
        Thread.sleep 5000
        http.request( DELETE, JSON ) {
            uri.path = "destroy/${postID}.json"
        }
    }

//  @Test
    public void testHeadMethod() {
        def http = new HTTPBuilder('https://api.twitter.com/1.1/statuses/')

        http.auth.oauth twitter.consumerKey, twitter.consumerSecret,
                twitter.accessToken, twitter.secretToken

        http.request( HEAD, XML ) {
            uri.path = 'friends_timeline.xml'

            response.success = { resp ->
                assert resp.getFirstHeader('Status').value == "200 OK"
                assert resp.headers.Status == "200 OK"
                assert resp.headers['Content-Encoding'].value == "gzip"
            }
        };
    }

    def "Request And Default Response Handlers"() {
        setup:
        def returnValue = 'my respondent handler!';
        def http = new HTTPBuilder()
        http.handler.'401' = { resp -> return 'access denied'; };
        http.handler.failure = { resp -> return "Unexpected failure: ${resp.statusLine}"; };
        http.uri = 'http://www.google.com';

        when:
        def result = http.request(GET,TEXT) { req ->
            response.success = { resp, stream ->
                return [ code: resp.statusLine.statusCode, value: returnValue ]; }; };
        
        then:
        result.code == 200;
        result.value == returnValue;
    }

    /**
     * Test a response handler that is assigned within a request config closure:
     */
    def "404"() {
        expect:
        'got 404' == new HTTPBuilder().request('http://www.google.com',GET,TEXT) {
            uri.path = '/asdfg/asasdfs' // should produce 404
            response.'404' = { return 'got 404' };
            response.success = { return 'did not get 404' }; };
    }

    /* http://googlesystem.blogspot.com/2008/04/google-search-rest-api.html
     * http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=Earth%20Day
     */
    @Ignore
    def "JSON"() {

        def builder = new HTTPBuilder()

//      builder.parser.'text/javascript' = builder.parsers."$JSON"

        builder.request('http://ajax.googleapis.com',GET,JSON) {
            uri.path = '/ajax/services/search/web'
            uri.query = [ v:'1.0', q: 'Earth Day' ]
            //UA header required to get Google to GZIP response:
            headers.'User-Agent' = "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.0.4) Gecko/2008111319 Ubuntu/8.10 (intrepid) Firefox/3.0.4"
            response.success = { resp, json ->
                assert resp.headers['Content-Encoding'].value == "gzip"
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

    def "Auth"() {
        setup:
        String authSuccessful = 'auth successful';
        String expectedFailure = 'expected failure';
        
        def http = new HTTPBuilder('http://test.webdav.org')
        /* The path issues a 404, but it does an auth challenge first. */
        http.handler.'404' = { return authSuccessful };

        when:
        def result = http.request( GET, HTML ) {
            uri.path = '/auth-digest/'
            response.failure = { return expectedFailure }
            response.success = { return 'unexpected success'; }; };

        then:
        result == expectedFailure;

        when:
        http.auth.basic('user2', 'user2');
        result = http.request( GET, HTML ) {
            uri.path = '/auth-digest/' };

        then:
        result == authSuccessful;

        when:
        result = http.request( GET, HTML ) {
            uri.path = '/auth-basic/'; };

        then:
        result == authSuccessful;
    }

    @Ignore
    def "Catalog"() {
        def http = new HTTPBuilder( 'http://weather.yahooapis.com/forecastrss' )
        http.parser.addCatalog getClass().getResource( '/rss-catalog.xml')
        def xml = http.get( query : [p:'02110',u:'f'] )
    }

    def "Invalid Named Arg"() {
        setup:
        def http = new HTTPBuilder('http://weather.yahooapis.com/forecastrss');

        when:
        def xml = http.get(query: [p:'02110',u:'f'], blah: 'asdf');

        then:
        thrown(IllegalArgumentException);
    }

    def "Should Throw Exception If Content Type Is Not Set"() {
        when:
        new HTTPBuilder( 'http://weather.yahooapis.com/forecastrss' ).request(POST) { request ->
            body = [p:'02110',u:'f']
        }

        then:
        thrown(IllegalArgumentException);
    }

    def "Urlenc Request Content Type"() {
        setup:
        def http = new HTTPBuilder('http://restmirror.appspot.com/')

        when:
        def statusCode = http.request(POST) {
            uri.path = '/'
            body =  [name: 'bob', title: 'construction worker']
            requestContentType = ContentType.URLENC
            
            response.success = { resp -> return resp.statusLine.statusCode; }; };

        then:
        statusCode == 201;
  }

    def "JSON Post"() {
        setup:
        def builder = new HTTPBuilder("http://restmirror.appspot.com/");

        when:
        def result = builder.request(POST, JSON) { req ->
            body = [name: 'bob', title: 'construction worker'];

            response.success = { resp, json -> return json; };
            
            response.failure = {resp -> return "JSON POST Failed: ${resp.statusLine}" }; };

        then:
        result instanceof Map;
        result.name == 'bob';
    }
}

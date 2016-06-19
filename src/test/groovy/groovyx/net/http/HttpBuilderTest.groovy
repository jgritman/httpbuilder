package groovyx.net.http;

import groovy.transform.TypeChecked;
import spock.lang.*
import java.util.function.Function;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import groovy.json.JsonSlurper;
import java.util.concurrent.Executors;

class HttpBuilderTest extends Specification {

    @Ignore
    def "Basic GET"() {
        setup:
        def result = HttpBuilder.singleThreaded().get {
            response.parser "text/html", NativeHandlers.Parsers.&textToString
            request.uri = 'http://www.google.com';
        };

        expect:
        result instanceof String;
        result.indexOf('</html>') != -1;
    }

    @Ignore
    def "GET with Parameters"() {
        setup:
        def http = HttpBuilder.singleThreaded().configure {
            response.parser "text/html", NativeHandlers.Parsers.&textToString
            request.uri = 'http://www.google.com';
        }

        String result = http.get(String) {
            request.uri.query = [ q: 'Big Bird' ];
        }

        expect:
        result.contains('Big Bird');
    }

    @Ignore
    def "Basic POST Form"() {
        setup:
        def toSend = [ foo: 'my foo', bar: 'my bar' ];
        def http = HttpBuilder.singleThreaded();
        def result = http.post {
            request.uri = 'http://httpbin.org/post'
            request.body = toSend;
            request.contentType = ContentTypes.URLENC[0];
        }

        expect:
        result;
        result.form == toSend;
    }

    @Ignore
    def "No Op POST Form"() {
        setup:
        def toSend = [ foo: 'my foo', bar: 'my bar' ];
        def http = HttpBuilder.singleThreaded().configure {
            request.uri = 'http://httpbin.org/post'
            request.body = toSend;
            request.contentType = ContentTypes.URLENC[0];
        }
        
        def result = http.post();

        expect:
        result;
        result.form == toSend;
    }

    @Ignore
    def "POST Json With Parameters"() {
        setup:
        def toSend = [ lastName: 'Count', firstName: 'The', address: [ street: '123 Sesame Street' ] ];
        def http = HttpBuilder.singleThreaded().configure {
            request.uri = 'http://httpbin.org/post'
            request.contentType = 'application/json';
        }

        def result = http.post {
            request.uri.query = [ one: '1', two: '2' ];
            request.accept = ContentTypes.JSON;
            request.body = toSend;
        }

        expect:
        result instanceof Map;
        result.headers.Accept.split(';') as List<String> == ContentTypes.JSON;
        new JsonSlurper().parseText(result.data) == toSend;
    }

    @Ignore
    def "Test POST Random Headers"() {
        setup:
        final headers = [ One: '1', Two: '2', Buckle: 'my shoe' ].asImmutable();
        def results = HttpBuilder.singleThreaded().post {
            request.uri = 'http://httpbin.org/post'
            request.contentType = 'application/json';
            request.headers = headers;
        }

        expect:
        headers.every { key, value -> results.headers[key] == value; };
    }

    @Ignore
    def "Test Head"() {
        setup:
        def result = HttpBuilder.singleThreaded().head {
            request.uri = 'http://www.google.com';
        };

        expect:
        !result
    }

    @Ignore
    def "Test Multi-Threaded Head"() {
        setup:
        def http = HttpBuilder.multiThreaded(2, Executors.newFixedThreadPool(2));
        def futures = (0..<2).collect {
            http.headAsync {
                request.uri = 'http://www.google.com';
            }
        }

        expect:
        futures.size() == 2;
        futures.every { future -> future.get() == null; };
    }

    @Ignore
    def "PUT Json With Parameters"() {
        setup:
        def toSend = [ lastName: 'Count', firstName: 'The', address: [ street: '123 Sesame Street' ] ];
        def http = HttpBuilder.singleThreaded().configure {
            request.uri = 'http://httpbin.org/put'
            request.contentType = 'application/json';
        }

        def result = http.put {
            request.uri.query = [ one: '1', two: '2' ];
            request.accept = ContentTypes.JSON;
            request.body = toSend;
        }

        expect:
        result instanceof Map;
        result.headers.Accept.split(';') as List<String> == ContentTypes.JSON;
        new JsonSlurper().parseText(result.data) == toSend;
    }

    @Ignore
    def "Gzip and Deflate"() {
        when:
        def gzipped = HttpBuilder.singleThreaded().get {
            request.uri = 'http://httpbin.org/gzip'
        }

        then:
        gzipped.gzipped == true;

        when:
        def deflated = HttpBuilder.singleThreaded().get {
            request.uri = 'http://httpbin.org/deflate'
        }

        then:
        deflated.deflated == true;
    }

    @Ignore
    def "Basic Auth"() {
        setup:
        def http = HttpBuilder.singleThreaded().configure {
            request.uri = 'http://httpbin.org'
        }

        def data = http.get {
            request.uri.path = '/basic-auth/barney/rubble'
            request.auth.basic 'barney', 'rubble'
        }

        expect:
        data.authenticated;
        data.user == 'barney'
    }

    def "Digest Auth"() {
        setup:
        def http = HttpBuilder.singleThreaded().configure {
            request.uri = 'http://httpbin.org'
            request.auth.digest 'david', 'clark'
        }

        when:
        def data = http.get {
            request.uri.path = '/digest-auth/auth/david/clark'
        }

        then:
        data.authenticated;
        data.user == 'david';
    }
}

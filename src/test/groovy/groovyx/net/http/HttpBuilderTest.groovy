package groovyx.net.http;

import spock.lang.*
import java.util.function.Function;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import groovy.json.JsonSlurper;

class HttpBuilderTest extends Specification {

    def "Basic GET"() {
        setup:
        def result = HttpBuilder.singleThreaded().get {
            parser "text/html", NativeHandlers.Parsers.&textToString
            request.uri = 'http://www.google.com';
        };

        expect:
        result instanceof String;
        result.indexOf('</html>') != -1;
    }

    def "GET with Parameters"() {
        setup:
        def http = HttpBuilder.singleThreaded().configure {
            parser "text/html", NativeHandlers.Parsers.&textToString
            request.uri = 'http://www.google.com';
        }

        String result = http.get(String) {
            request.uri.query = [ q: 'Big Bird' ];
        }

        expect:
        result.contains('Big Bird');
    }

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

    def "POST Json With Parameters"() {
        setup:
        def toSend = [ lastName: 'Count', firstName: 'The', address: [ street: '123 Sesame Street' ] ];
        def http = HttpBuilder.singleThreaded().configure {
            request.uri = 'http://httpbin.org/post'
            request.contentType = 'application/json';
        }

        def result = http.post {
            request.uri.query = [ one: '1', two: '2' ];
            request.body = toSend;
        }

        expect:
        result instanceof Map;
        new JsonSlurper().parseText(result.data) == toSend;
    }
}

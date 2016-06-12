package groovyx.net.http;

import spock.lang.*
import java.util.function.Function;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

class HttpConfigTest extends Specification {

    def "Basic Config"() {
        setup:
        Function jsonEncoder = NativeHandlers.Encoders.&json;
        Function jsonParser = NativeHandlers.Parsers.&json;

        HttpConfig http = AbstractHttpConfig.threadSafe().config {
            request.charset = StandardCharsets.UTF_8;
            request.contentType = "application/json";
            request.uri = 'http://www.google.com';
            request.body = [ one: 1, two: 2 ];

            def JSON = ["application/json", "application/javascript", "text/javascript"];
            encoder JSON, jsonEncoder
            parser JSON, jsonParser
        }

        expect:
        http.encoder("application/json") == jsonEncoder;
        http.parser("text/javascript") == jsonParser;
        http.encoder("application/javascript").apply(http.request) != null;
        http.request.body == [ one: 1, two: 2 ];
    }

    def Chaining() {
        setup:
        Function xmlEncoder = NativeHandlers.Encoders.&xml;
        Function xmlParser = NativeHandlers.Parsers.&xml;
        Closure success = { res, o -> println(o); }
        Closure failure = { res -> println("failed"); }
        Closure on404 = { res -> println('why u 404?'); }
        
        def theBody = { ->
            root {
                person {
                    firstName 'Fred'
                    lastName 'Flinstone' }; }; };
        String contentType = 'application/xml';
        Charset charset = StandardCharsets.UTF_8;
        URIBuilder uriBuilder = new URIBuilder('http://www.yahoo.com/likes/xml')
        def XML = ["application/xml","text/xml","application/xhtml+xml","application/atom+xml"];
        
        def root = AbstractHttpConfig.threadSafe().config {
            request.charset = charset
            encoder XML, xmlEncoder
            response.success = success;
            response.failure = failure;
        };

        def intermediate = AbstractHttpConfig.threadSafe(root).config {
            request.contentType = contentType
            request.uri = uriBuilder
            parser XML, xmlParser
        };

        def end = AbstractHttpConfig.basic(intermediate).config {
            request.body = theBody;
            response.when 404, on404;
        };

        expect:
        end.effectiveEncoder(contentType) == xmlEncoder;
        end.effectiveParser(contentType) == xmlParser;
        end.request.effectiveBody() == theBody;
        end.request.effectiveContentType() == contentType;
        end.request.effectiveCharset() == charset;
        end.request.effectiveUri() == uriBuilder;

        end.response.effectiveAction(200) == success;
        end.response.effectiveAction(400) == failure;
        end.response.effectiveAction(404) == on404;
        intermediate.response.effectiveAction(404) == failure;

        end.acceptHeader('application/xml') == XML as Set;
    }
}

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
            request.encoder JSON, jsonEncoder
            response.parser JSON, jsonParser
        }

        expect:
        http.request.encoder("application/json") == jsonEncoder;
        http.response.parser("text/javascript") == jsonParser;
        http.request.encoder("application/javascript").apply(http.request.effective) != null;
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
            request.encoder XML, xmlEncoder
            response.success = success;
            response.failure = failure;
        };

        def intermediate = AbstractHttpConfig.threadSafe(root).config {
            request.contentType = contentType
            request.uri = uriBuilder
            response.parser XML, xmlParser
        };

        def end = AbstractHttpConfig.basic(intermediate).config {
            request.body = theBody;
            response.when 404, on404;
        };

        expect:
        end.request.effective.encoder(contentType) == xmlEncoder;
        end.response.effective.parser(contentType) == xmlParser;
        end.request.effective.body() == theBody;
        end.request.effective.contentType() == contentType;
        end.request.effective.charset() == charset;
        end.request.effective.uri() == uriBuilder;

        end.response.effective.action(200) == success;
        end.response.effective.action(400) == failure;
        end.response.effective.action(404) == on404;
        intermediate.response.effective.action(404) == failure;
    }

    def Script() {
        setup:
        def config = AbstractHttpConfig.basic(null).configure('script1.groovy');

        expect:
        config.request.contentType == 'application/json';
        config.request.uri == new URIBuilder('http://www.google.com');
    }
}

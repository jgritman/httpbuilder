package groovyx.net.http;

import org.apache.http.ProtocolVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import spock.lang.*;
import static groovyx.net.http.ContentType.*;

class RegistryTest extends Specification{

    def "Parser Registry"() {
        setup:
        def newParser = {}
        
        when:
        def reg = new ParserRegistry();

        then:
        reg.defaultParser;
        reg.every { it.key && it.value; };
        reg."text/plain"
        reg["text/plain"]
        
        when:
        reg."application/json" = newParser;

        then:
        reg['application/json'].is(newParser);

        when:
        reg[ContentType.JSON] = newParser;

        then:
        reg[ "application/javascript" ].is(newParser);

        when:
        reg.defaultParser = newParser;

        then:
        newParser.is(reg.defaultParser);
    }

    def "Encoder Accessors"() {
        setup:
        def newEnc = {};

        when:
        def reg = new EncoderRegistry();
        
        then:
        reg.every { it.key && it.value; };
        reg."text/plain"
        reg["text/plain"]
        
        when:
        reg."application/json" = newEnc;
        reg['application/xml'] = newEnc;

        then:
        reg['application/json'].is(newEnc);
        reg."application/xml".is(newEnc);
    }
    
    def "XML Encoder"() {
        setup:
        def reg = new EncoderRegistry();
        def value = 'something'
                
        when:
        def entity = reg.encodeXML( {
            xml(AAA: 'aaa') {
                one 'one'
                two 'two'
            } }, 'text/xml');
        
        then:
        entity.contentType.value == "text/xml"
        entity.content.text == "<xml AAA='aaa'><one>one</one><two>two</two></xml>"

        when:
        entity = reg.encodeXML( '<xml AAA="aaa"><one>one</one><two>two</two></xml>', null);

        then:
        entity.content.text == '<xml AAA="aaa"><one>one</one><two>two</two></xml>';
        
        when:
        entity = reg.encodeXML("<xml AAA='aaa'><one>${value}</one><two>two</two></xml>",null);

        then:
        entity.content.text == "<xml AAA='aaa'><one>something</one><two>two</two></xml>"
    }
    
    def "Charset And Text"() {
        setup:
        def reg = new EncoderRegistry( charset: "ISO-8859-1" );

        when:
        def entity = reg.encodeText({ out -> out << "This is a test"; }, null);

        then:
        entity.contentType.value == 'text/plain';
        entity.content.getText('ISO-8859-1') == "This is a test";
        entity.content.getText('utf-16') != "This is a test";

        when:
        def w = { it << "this is a test 1" } as Writable;
        entity = reg.encodeText( w, 'text/plain' );

        then:
        entity.content.getText('ISO-8859-1') == "this is a test 1";

        when:
        entity = reg.encodeText("This is a test 2", 'text/notplain');

        then:
        entity.contentType.value == 'text/notplain';
        entity.content.getText('ISO-8859-1') == "This is a test 2";

        when:
        entity = reg.encodeText(new StringReader("This is a test 3\nMore text"), 'text/plain');

        then:
        entity.contentType.value == 'text/plain'     
        entity.content.getText('ISO-8859-1') == "This is a test 3\nMore text"
    }
    
    def "Stream"() {
        setup:
        def reg = new EncoderRegistry();
        def data = [ 0x0, 0x1, 0x2 ] as byte[]

        when:
        def entity = reg.encodeStream({ out -> out << data }, null);

        then:
        entity.contentType.value == 'application/octet-stream'
        entity.contentLength == data.length

        when:
        def result = new ByteArrayOutputStream() 
        result << entity.content

        then:
        result.toByteArray() == data

        when:
        entity = reg.encodeStream(new ByteArrayInputStream(data), 'application/x-gzip');

        then:
        entity.contentLength == data.length
        entity.contentType.value == 'application/x-gzip'

        when:
        result = new ByteArrayOutputStream() ;
        result << entity.content

        then:
        result.toByteArray() == data

        when:
        entity = reg.encodeStream( data, null ) // byte[]
        
        then:
        entity.contentLength == data.length;

        when:
        result = new ByteArrayOutputStream();
        result << entity.content;

        then:
        result.toByteArray() == data;

        when:
        entity = reg.encodeStream( result, null ) // ByteArrayOutputStream

        then:
        entity.contentLength == data.length;

        when:
        result = new ByteArrayOutputStream() ;
        result << entity.content;

        then:
        result.toByteArray() == data;
    }
    
    def "JSON Encoder"() {
        setup:
        def another = 'second';
        def reg = new EncoderRegistry();
        def jsonMap = [ first : [ one: 1, two: "2" ],
                        second : 'some string' ];
        def jsonClosure = {
            root {
                first {
                    one 1
                    two '2'
                }
                second 'some string'
            } };
        when:
        def entity = reg.encodeJSON(jsonMap, null);

        then:
        entity.contentType.value == "application/json"
        entity.content.text == '{"first":{"one":1,"two":"2"},"second":"some string"}';

        when:
        entity = reg.encodeJSON(["first", "second", 3, [map:4] ], 'text/javascript');

        then:
        entity.contentType.value == "text/javascript";
        entity.content.text == '["first","second",3,{"map":4}]';

        when:
        entity = reg.encodeJSON(jsonClosure, null);

        then:
        entity.content.text == '{"root":{"first":{"one":1,"two":"2"},"second":"some string"}}'

        when:
        entity = reg.encodeJSON('["first","second",3,{"map":4}]', null);

        then:
        entity.content.text == '["first","second",3,{"map":4}]';

        when:
        entity = reg.encodeJSON("['first','$another',3,{'map':4}]",null);

        then:
        entity.content.text == "['first','second',3,{'map':4}]";
    }
    
    def "Form Encoder"() {
        setup:
        def enc = new EncoderRegistry();
        def param1 = "p1";

        when:
        def entity = enc.encodeForm(["${param1}":'one', p2:['two','three']]);

        then:
        entity.contentType.elements[0].name == 'application/x-www-form-urlencoded'
        entity.content.text == "p1=one&p2=two&p2=three"

        when:
        entity = enc.encodeForm( "p1=goober&p2=something+else",null )

        then:
        entity.contentType.value == 'application/x-www-form-urlencoded'
        entity.content.text == "p1=goober&p2=something+else"
    }
    
    def "Form Parser"() {
        setup:
        def parser = new ParserRegistry()
        def entity = new StringEntity("p1=goober&p2=something+else", "utf-8")
        // GMOD-137: URLENC parsing doesn't work w/ bad content-type
        entity.setContentType "text/plain" // NOT application/x-www-form-urlencoded

        when:
        def response = new BasicHttpResponse(new ProtocolVersion( "HTTP", 1, 1 ), 200, "OK");
        response.entity = entity;
        def map = parser.parseForm(response);

        then:
        map
        map.p1 == 'goober'
        map.p2 == 'something else' 
    }
}

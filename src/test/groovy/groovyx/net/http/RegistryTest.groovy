package groovyx.net.http

import org.apache.http.ProtocolVersion
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHttpResponse
import org.junit.Testimport java.io.StringReaderimport java.io.ByteArrayInputStream
import static groovyx.net.http.ContentType.*
/**
 * @author tnichols
 */
public class RegistryTest {

	@Test public void testParserRegistry() {
		def reg = new ParserRegistry();
		
		assert reg.defaultParser
		reg.each { assert it.key && it.value }

		assert reg."text/plain"
		assert reg["text/plain"]
		
		def newParser = {}
		reg."application/json" = newParser
		assert reg['application/json'].is( newParser )
		
		reg[ ContentType.JSON ] = newParser
		assert reg[ "application/javascript" ].is( newParser )
		
		reg.defaultParser = newParser
		assert newParser.is( reg.defaultParser )
	}

	@Test public void testEncoderAccessors() {
		def reg = new EncoderRegistry();
		
		reg.each { assert it.key && it.value }

		assert reg."text/plain"
		assert reg["text/plain"]
		
		def newEnc = {}
		reg."application/json" = newEnc
		reg['application/xml'] = newEnc
		assert reg['application/json'].is( newEnc )
		assert reg."application/xml".is( newEnc )
	}
	
	@Test public void testXMLEncoder() {
		def reg = new EncoderRegistry();
		
		def entity = reg.encodeXML( {
			xml( AAA: 'aaa' ) {
				one 'one'
				two 'two'
			}
		}, 'text/xml' )
		
		assert entity.contentType.value == "text/xml"
//		println entity.content.text
		assert entity.content.text == "<xml AAA='aaa'><one>one</one><two>two</two></xml>"
		
		entity = reg.encodeXML( '<xml AAA="aaa"><one>one</one><two>two</two></xml>',null )
		assert entity.content.text == '<xml AAA="aaa"><one>one</one><two>two</two></xml>'
		def value = 'something'
		entity = reg.encodeXML( "<xml AAA='aaa'><one>$value</one><two>two</two></xml>",null )
		assert entity.content.text == "<xml AAA='aaa'><one>something</one><two>two</two></xml>"
	}
	
	@Test public void testCharsetAndText() {
		def reg = new EncoderRegistry( charset: "ISO-8859-1" );
		
		def entity = reg.encodeText( { out ->
			out << "This is a test"
		}, null )
		
		assert entity.contentType.value == 'text/plain'
		assert entity.content.getText('ISO-8859-1') == "This is a test"
		assert entity.content.getText('utf-16') != "This is a test"
		
		def w = { it << "this is a test 1" } as Writable
		entity = reg.encodeText( w, 'text/plain' )
		assert entity.content.getText('ISO-8859-1') == "this is a test 1"
		
		entity = reg.encodeText( "This is a test 2", 'text/notplain' )
		assert entity.contentType.value == 'text/notplain'
		assert entity.content.getText('ISO-8859-1') == "This is a test 2"

		entity = reg.encodeText( new StringReader( "This is a test 3\nMore text"), 'text/plain' )
		assert entity.contentType.value == 'text/plain'		
		assert entity.content.getText('ISO-8859-1') == "This is a test 3\nMore text"
	}
	
	@Test public void testStream() {
		def reg = new EncoderRegistry();
		
		def data = [ 0x0, 0x1, 0x2 ] as byte[]
		
		def entity = reg.encodeStream( { out ->  // closure
			out << data
		}, null )
		
		assert entity.contentType.value == 'application/octet-stream'
		assert entity.contentLength == data.length
		def result = new ByteArrayOutputStream() 
		result << entity.content
		assert result.toByteArray() == data
		
		entity = reg.encodeStream( new ByteArrayInputStream(data), 'application/x-gzip' )
		assert entity.contentLength == data.length
		assert entity.contentType.value == 'application/x-gzip'
		result = new ByteArrayOutputStream() 
		result << entity.content
		assert result.toByteArray() == data
		
		entity = reg.encodeStream( data, null ) // byte[]
		assert entity.contentLength == data.length
		result = new ByteArrayOutputStream() 
		result << entity.content
		assert result.toByteArray() == data
		
		entity = reg.encodeStream( result, null ) // ByteArrayOutputStream
		assert entity.contentLength == data.length
		result = new ByteArrayOutputStream() 
		result << entity.content
		assert result.toByteArray() == data
	}
	
	@Test public void testJSONEncoder() {
		def reg = new EncoderRegistry();
		
		def entity = reg.encodeJSON( [
				first : [ one:1, two:"2" ],
				second : 'some string'
		], null )
		
		assert entity.contentType.value == "application/json"
//		println entity.content.text
		assert entity.content.text == '{"first":{"one":1,"two":"2"},"second":"some string"}'
		
		entity = reg.encodeJSON( ["first", "second", 3, [map:4] ], 'text/javascript' )
		assert entity.contentType.value == "text/javascript"
  		assert entity.content.text == '["first","second",3,{"map":4}]'
  		
  		entity = reg.encodeJSON( {
			root {
				first {
					one = 1
					two = '2'
				}
				second = 'some string'
			}
		}, null )
		
//		println entity.content.text
		assert entity.content.text == '{"root":{"first":{"one":1,"two":"2"},"second":"some string"}}'
		
		entity = reg.encodeJSON( '["first","second",3,{"map":4}]',null )
  		assert entity.content.text == '["first","second",3,{"map":4}]'
		def another = 'second'
		entity = reg.encodeJSON( "['first','$another',3,{'map':4}]",null )
  		assert entity.content.text == "['first','second',3,{'map':4}]"
	}
	
	@Test public void testFormEncoder() {
		def enc = new EncoderRegistry()
		
		def param1 = "p1"
		def entity = enc.encodeForm( ["${param1}":'one', p2:['two','three']] )
		
		assert entity.contentType.elements[0].name == 'application/x-www-form-urlencoded'
		assert entity.content.text == "p1=one&p2=two&p2=three"
		
		entity = enc.encodeForm( "p1=goober&p2=something+else",null )
		assert entity.contentType.value == 'application/x-www-form-urlencoded'
		assert entity.content.text == "p1=goober&p2=something+else"
	}
	
	@Test public void testFormParser() {
		def parser = new ParserRegistry()

		def entity = new StringEntity( "p1=goober&p2=something+else", "utf-8" )
		// GMOD-137: URLENC parsing doesn't work w/ bad content-type
		entity.setContentType "text/plain" // NOT application/x-www-form-urlencoded
		
		def response = new BasicHttpResponse( new ProtocolVersion( "HTTP", 1, 1 ), 200, "OK" )
		response.entity = entity
		def map = parser.parseForm( response )
		assert map
		assert map.p1 == 'goober'
		assert map.p2 == 'something else' 
	}
}

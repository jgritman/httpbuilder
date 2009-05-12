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

import groovy.lang.Closure;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import groovyx.net.http.HTTPBuilder.RequestConfigDelegate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import net.sf.json.JSON;
import net.sf.json.groovy.JsonSlurper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.MethodClosure;
import org.cyberneko.html.parsers.SAXParser;
import org.xml.sax.SAXException;


/**
 * <p>Keeps track of response parsers for each content type.  Each parser 
 * should should be a closure that accepts an {@link HttpResponse} instance,
 * and returns whatever handler is appropriate for reading the response 
 * data for that content-type.  For example, a plain-text response should 
 * probably be parsed with a <code>Reader</code>, while an XML response 
 * might be parsed by an XmlSlurper, which would then be passed to the 
 * response closure. </p>
 * 
 * <p>Note that all methods in this class assume {@link HttpResponse#getEntity()}
 * return a non-null value.  It is the job of the HTTPBuilder instance to ensure
 * a NullPointerException is not thrown by passing a response that contains no
 * entity.</p>
 * 
 * @see ContentType
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 */
public class ParserRegistry {
	
	/**
	 * The default parser used for unregistered content-types.  This is a copy 
	 * of {@link #parseStream(HttpResponse)}, which is like a no-op that just 
	 * returns the unaltered response stream.
	 */
	protected final Closure DEFAULT_PARSER = new MethodClosure( this, "parseStream" );

	private Closure defaultParser = DEFAULT_PARSER;
	private Map<String,Closure> registeredParsers = buildDefaultParserMap();
	
	protected final Log log = LogFactory.getLog( getClass() );
	
	/**
	 * Helper method to get the charset from the response.  This should be done 
	 * when manually parsing any text response to ensure it is decoded using the
	 * correct charset. For instance:<pre>
	 * Reader reader = new InputStreamReader( resp.getEntity().getContent(), 
	 *   ParserRegistry.getCharset( resp ) );</pre>
	 * @param resp
	 */
	public static String getCharset( HttpResponse resp ) {
		NameValuePair charset = resp.getEntity().getContentType()
				.getElements()[0].getParameterByName("charset"); 
		return ( charset == null || charset.getValue().trim().equals("") ) ?
			Charset.defaultCharset().name() : charset.getValue();
	}
	
	/**
	 * Helper method to get the content-type string from the response 
	 * (no charset).
	 * @param resp
	 */
	public static String getContentType( HttpResponse resp ) {
		/* TODO how do we handle a very rude server who does not return a 
		   content-type header?  It could cause an NPE here. and in getCharset */
		return resp.getEntity().getContentType()
			.getElements()[0].getName();
	}
	
	/**
	 * Default parser used for binary data.  This simply returns the underlying
	 * response InputStream.
	 * @see ContentType#BINARY
	 * @see HttpEntity#getContent()
	 * @param resp
	 * @return an InputStream the binary response stream
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public InputStream parseStream( HttpResponse resp ) throws IOException {
		return resp.getEntity().getContent();
	}
	
	/**
	 * Default parser used to handle plain text data.  The response text 
	 * is decoded using the charset passed in the response content-type 
	 * header. 
	 * @see ContentType#TEXT
	 * @param resp
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public Reader parseText( HttpResponse resp ) throws IOException {
		return new InputStreamReader( resp.getEntity().getContent(), 
				ParserRegistry.getCharset( resp ) );
	}
	
	/**
	 * Default parser used to decode a URL-encoded response.
	 * @see ContentType#URLENC
	 * @param resp
	 * @return
	 * @throws IOException
	 */
	public Map<String,String> parseForm( HttpResponse resp ) throws IOException {
		List<NameValuePair> params = URLEncodedUtils.parse( resp.getEntity() );
		Map<String,String> paramMap = new HashMap<String,String>(params.size());
		for ( NameValuePair param : params ) 
			paramMap.put( param.getName(), param.getValue() );
		return paramMap;
	}
	
	/**
	 * Parse an HTML document by passing it through the NekoHTML parser.
	 * @see ContentType#HTML
	 * @see SAXParser
	 * @see XmlSlurper#parse(Reader)
	 * @param resp HTTP response from which to parse content
	 * @return the {@link GPathResult} from calling {@link XmlSlurper#parse(Reader)}
	 * @throws IOException
	 * @throws SAXException
	 */
	public GPathResult parseHTML( HttpResponse resp ) throws IOException, SAXException {
		return new XmlSlurper( new org.cyberneko.html.parsers.SAXParser() )
			.parse( parseText( resp ) );
	}
	
	/**
	 * Default parser used to decode an XML response.  
	 * @see ContentType#XML
	 * @see XmlSlurper#parse(Reader)
	 * @param resp HTTP response from which to parse content
	 * @return the {@link GPathResult} from calling {@link XmlSlurper#parse(Reader)}
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	public GPathResult parseXML( HttpResponse resp ) throws IOException, SAXException, ParserConfigurationException {
		return new XmlSlurper().parse( parseText( resp ) );
	}
	
	/**
	 * Default parser used to decode a JSON response.
	 * @see ContentType#JSON
	 * @param resp
	 * @return
	 * @throws IOException
	 */
	public JSON parseJSON( HttpResponse resp ) throws IOException {
		// there is a bug in the JsonSlurper.parse method...
		String jsonTxt = DefaultGroovyMethods.getText( parseText( resp ) );			
		return new JsonSlurper().parseText( jsonTxt );
	}
	
	/**
	 * Returns a map of default parsers.  Override this method to change 
	 * what parsers are registered by default.  You can of course call
	 * <code>super.buildDefaultParserMap()</code> and then add or remove 
	 * from that result as well.
	 */
	protected Map<String,Closure> buildDefaultParserMap() {
		Map<String,Closure> parsers = new HashMap<String,Closure>();
		
		parsers.put( ContentType.BINARY.toString(), new MethodClosure( this, "parseStream" ) );
		parsers.put( ContentType.TEXT.toString(), new MethodClosure(this,"parseText") );
		parsers.put( ContentType.URLENC.toString(), new MethodClosure(this,"parseForm") );
		parsers.put( ContentType.HTML.toString(), new MethodClosure(this,"parseHTML") );
		
		Closure pClosure = new MethodClosure(this,"parseXML");
		for ( String ct : ContentType.XML.getContentTypeStrings() )
			parsers.put( ct, pClosure );
		
		pClosure = new MethodClosure(this,"parseJSON");
		for ( String ct : ContentType.JSON.getContentTypeStrings() )
			parsers.put( ct, pClosure );
		
		return parsers;
	}
	
	/**
	 * Get the default parser used for unregistered content-types.
	 * @return
	 */
	public Closure getDefaultParser() {
		return this.defaultParser;
	}
	
	/**
	 * Set the default parser used for unregistered content-types.
	 * @param defaultParser if 
	 */
	public void setDefaultParser( Closure defaultParser ) {
		if ( defaultParser == null ) this.defaultParser = DEFAULT_PARSER;
		this.defaultParser = defaultParser;
	}

	/** 
	 * Retrieve a parser for the given response content-type string.  This
	 * is called by HTTPBuildre to retrieve the correct parser for a given 
	 * content-type.  The parser is then used to decode the response data prior
	 * to passing it to a response handler. 
	 * @param contentType
	 * @return parser that can interpret the given response content type,
	 *   or the default parser if no parser is registered for the given 
	 *   content-type.  It should NOT return a null value.
	 */
	public Closure getAt( Object contentType ) {
		String ct = contentType.toString();
		int idx = ct.indexOf( ';' ); 
		if ( idx > 0 ) ct = ct.substring( 0, idx );
		
		Closure parser = registeredParsers.get(ct);
		if ( parser != null ) return parser;

		log.warn( "Cannot find parser for content-type: " + ct 
					+ " -- using default parser.");
		return defaultParser;
	}
	
	/**
	 * Register a new parser for the given content-type.  The parser closure
	 * should accept an {@link HttpResponse} argument and return a type suitable
	 * to be passed as the 'parsed data' argument of a 
	 * {@link RequestConfigDelegate#getResponse() response handler} closure.
	 * @param contentType  <code>content-type</code> string
	 * @param closure code that will parse the HttpResponse and return parsed 
	 *   data to the response handler. 
	 */
	public void setAt( Object contentType, Closure value ) {
		if ( contentType instanceof ContentType ) {
			for ( String ct : ((ContentType)contentType).getContentTypeStrings() )
				this.registeredParsers.put( ct, value );
		}
		else this.registeredParsers.put( contentType.toString(), value );
	}
	
	/**
	 * Alias for {@link #getAt(Object)} to allow property-style access.
	 * @param key content-type string
	 * @return
	 */
	public Closure propertyMissing( Object key ) {
		return this.getAt( key );
	}
	
	/**
	 * Alias for {@link #setAt(Object, Closure)} to allow property-style access.
	 * @param key content-type string
	 * @param value parser closure
	 */
	public void propertyMissing( Object key, Closure value ) {
		this.setAt( key, value );
	}
	
	/**
	 * Iterate over the entire parser map
	 * @return
	 */
	public Iterator<Map.Entry<String,Closure>> iterator() { 
		return this.registeredParsers.entrySet().iterator(); 
	}
}
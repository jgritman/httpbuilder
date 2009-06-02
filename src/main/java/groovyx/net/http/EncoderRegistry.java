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
import groovy.lang.Writable;
import groovy.xml.StreamingMarkupBuilder;
import groovyx.net.http.HTTPBuilder.RequestConfigDelegate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonGroovyBuilder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.MethodClosure;


/**
 * <p>This class handles creation of the request body (i.e. for a 
 * PUT or POST operation) based on content-type.   When a 
 * {@link RequestConfigDelegate#setBody(Object) body} is set from the builder, it is 
 * processed based on the {@link RequestConfigDelegate#getRequestContentType() 
 * request content-type}.  For instance, the {@link #encodeForm(Map)} method 
 * will be invoked if the request content-type is form-urlencoded, which will 
 * cause the following:<code>body=[a:1, b:'two']</code> to be encoded as 
 * the equivalent <code>a=1&b=two</code> in the request body.</p>
 * 
 * <p>Most default encoders can handle a closure as a request body.  In this 
 * case, the closure is executed and a suitable 'builder' passed to the 
 * closure that is  used for constructing the content.  In the case of 
 * binary encoding this would be an OutputStream; for TEXT encoding it would
 * be a PrintWriter, and for XML it would be an already-bound 
 * {@link StreamingMarkupBuilder}. See each <code>encode...</code> method 
 * for details for each particular content-type.</p>
 * 
 * <p>Contrary to its name, this class does not have anything to do with the 
 * <code>content-encoding</code> HTTP header.  </p>
 * 
 * @see RequestConfigDelegate#setBody(Object)
 * @see RequestConfigDelegate#send(Object, Object)
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 */
public class EncoderRegistry {
	
	Charset charset = Charset.defaultCharset(); // 1.5
	private Map<String,Closure> registeredEncoders = buildDefaultEncoderMap();

	/**
	 * Set the charset used in the content-type header of all requests that send
	 * textual data.  This must be a chaset supported by the Java platform
	 * @see Charset#forName(String)
	 * @param charset 
	 */
	public void setCharset( String charset ) { 
		this.charset = Charset.forName(charset);
	}
	
	/**
	 * Default request encoder for a binary stream.  Acceptable argument 
	 * types are:
	 * <ul>
	 *   <li>InputStream</li>
	 *   <li>byte[] / ByteArrayOutputStream</li>
	 *   <li>Closure</li>
	 * </ul>
	 * If a closure is given, it is executed with an OutputStream passed
	 * as the single closure argument.  Any data sent to the stream from the 
	 * body of the closure is used as the request content body.
	 * @param data
	 * @return an {@link HttpEntity} encapsulating this request data
	 * @throws UnsupportedEncodingException
	 */
	public InputStreamEntity encodeStream( Object data ) throws UnsupportedEncodingException {
		InputStreamEntity entity = null;
		
		if ( data instanceof ByteArrayInputStream ) {
			// special case for ByteArrayIS so that we can set the content length.
			ByteArrayInputStream in = ((ByteArrayInputStream)data);
			entity = new InputStreamEntity( in, in.available() );
		}
		else if ( data instanceof InputStream ) {
			entity = new InputStreamEntity( (InputStream)data, -1 );
		}
		else if ( data instanceof byte[] ) {
			byte[] out = ((byte[])data); 
			entity = new InputStreamEntity( new ByteArrayInputStream(
					out), out.length );
		}
		else if ( data instanceof ByteArrayOutputStream ) {
			ByteArrayOutputStream out = ((ByteArrayOutputStream)data); 
			entity = new InputStreamEntity( new ByteArrayInputStream(
					out.toByteArray()), out.size() );
		}
		else if ( data instanceof Closure ) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			((Closure)data).call( out ); // data is written to out
			entity = new InputStreamEntity( new ByteArrayInputStream( 
					out.toByteArray()), out.size() );
		}

		if ( entity == null ) throw new IllegalArgumentException( 
				"Don't know how to encode " + data + " as a byte stream" );
		
		entity.setContentType( ContentType.BINARY.toString() );
		return entity;
	}
	
	/**
	 * Default handler used for a plain text content-type.  Acceptable argument
	 * types are:
	 * <ul>
	 *   <li>Closure</li>
	 *   <li>Writable</li>
	 *   <li>Reader</li>
	 * </ul>
	 * For Closure argument, a {@link PrintWriter} is passed as the single 
	 * argument to the closure.  Any data sent to the writer from the 
	 * closure will be sent to the request content body.
	 * @param data
	 * @return an {@link HttpEntity} encapsulating this request data
	 * @throws IOException
	 */
	public HttpEntity encodeText( Object data ) throws IOException {
		if ( data instanceof Closure ) {
			StringWriter out = new StringWriter();
			PrintWriter writer = new PrintWriter( out );
			((Closure)data).call( writer );
			writer.close();
			out.flush();
			data = out;
		}
		else if ( data instanceof Writable ) {
			StringWriter out = new StringWriter();
			((Writable)data).writeTo(out);
			out.flush();
			data = out;
		}
		else if ( data instanceof Reader && ! (data instanceof BufferedReader) )
			data = new BufferedReader( (Reader)data );
		if ( data instanceof BufferedReader ) {
			StringWriter out = new StringWriter();
			DefaultGroovyMethods.leftShift( out, (BufferedReader)data );
			
			data = out;
		}
		// if data is a String, we are already covered.
		return createEntity( ContentType.TEXT, data.toString() );
	}
	
	/**
	 * Set the request body as a url-encoded list of parameters.  This is 
	 * typically used to simulate a HTTP form POST. 
	 * @param params
	 * @return an {@link HttpEntity} encapsulating this request data
	 * @throws UnsupportedEncodingException
	 */
	public UrlEncodedFormEntity encodeForm( Map<String,Object> params ) 
			throws UnsupportedEncodingException {
		List<NameValuePair> paramList = new ArrayList<NameValuePair>();

		for ( Map.Entry<String, Object> entry : params.entrySet() ) 
			paramList.add( new BasicNameValuePair(entry.getKey(), 
					entry.getValue().toString()) );
			
		return new UrlEncodedFormEntity( paramList, charset.name() );
	}
	
	/**
	 * Accepts a closure which is interpreted as an XML structure that would be 
	 * passed to {@link StreamingMarkupBuilder#bind(groovy.lang.Closure)}.
	 * @param xmlBuilder builder closure that defines the XML structure
	 * @return an {@link HttpEntity} encapsulating this request data
	 * @throws UnsupportedEncodingException
	 */
	public HttpEntity encodeXML( Closure xmlBuilder ) throws UnsupportedEncodingException {
		StreamingMarkupBuilder smb = new StreamingMarkupBuilder();
		String markup = smb.bind( xmlBuilder ).toString();
		return createEntity( ContentType.XML, markup );
	}
	
	/**
	 * <p>Accepts a Collection or a JavaBean object which is converted to JSON.  
	 * A Map or POJO/POGO will be converted to a {@link JSONObject}, and any 
	 * other collection type will be converted to a {@link JSONArray}.</p> 
	 * 
	 * <p>If a Closure is passed as the model, it will be executed as if it were 
	 * a JSON object definition passed to a {@link JsonGroovyBuilder}.  In order
	 * for the closure to be interpreted correctly, there must be a 'root' 
	 * element immediately inside the closure.  For example:</p>
	 * 
	 * <pre>builder.post( JSON ) {
	 *   body = {
	 *     root {
	 *       first {
	 *         one = 1
	 *         two = '2'
	 *       }
	 *       second = 'some string'
	 *     }
	 *   }
	 * }</pre>
	 * <p> will return the following JSON string:<pre>
	 * {"root":{"first":{"one":1,"two":"2"},"second":"some string"}}</pre></p>
	 *  
	 * @param model data to be converted to JSON, as specified above.
	 * @return an {@link HttpEntity} encapsulating this request data
	 * @throws UnsupportedEncodingException
	 */
	@SuppressWarnings("unchecked")
	public HttpEntity encodeJSON( Object model ) throws UnsupportedEncodingException {
		
		JSON json;	
		if ( model instanceof Map ) {
			json = new JSONObject();
			((JSONObject)json).putAll( (Map)model );
		}
		else if ( model instanceof Collection ) {
			json = new JSONArray();
			((JSONArray)json).addAll( (Collection)model );
		}
		else if ( model instanceof Closure ) {
			Closure closure = (Closure)model;
			closure.setDelegate( new JsonGroovyBuilder() );
			json = (JSON)closure.call();
		}
		else json = JSONObject.fromObject( model ); // Assume object is a JavaBean
		
		return this.createEntity( ContentType.JSON, json.toString() );
	}
	
	/**
	 * Helper method used by encoder methods to create an {@link HttpEntity} 
	 * instance that encapsulates the request data.  This may be used by any 
	 * non-streaming encoder that needs to send textual data.  It also sets the 
	 * {@link #setCharset(String) charset} portion of the content-type header. 
	 * 
	 * @param ct content-type of the data
	 * @param data textual request data to be encoded 
	 * @return an instance to be used for the 
	 *  {@link HttpEntityEnclosingRequest#setEntity(HttpEntity) request content} 
	 * @throws UnsupportedEncodingException
	 */
	protected StringEntity createEntity( ContentType ct, String data ) 
			throws UnsupportedEncodingException {
		StringEntity entity = new StringEntity( data, charset.toString() );
		entity.setContentType( ct.toString() );
		return entity;
	}
	
	/**
	 * Returns a map of default encoders.  Override this method to change 
	 * what encoders are registered by default.  You can of course call
	 * <code>super.buildDefaultEncoderMap()</code> and then add or remove 
	 * from that result as well.
	 */
	protected Map<String,Closure> buildDefaultEncoderMap() {
		Map<String,Closure> encoders = new HashMap<String,Closure>();
		
		encoders.put( ContentType.BINARY.toString(), new MethodClosure(this,"encodeStream") );
		encoders.put( ContentType.TEXT.toString(), new MethodClosure( this, "encodeText" ) );
		encoders.put( ContentType.URLENC.toString(), new MethodClosure( this, "encodeForm" ) );
		
		Closure encClosure = new MethodClosure(this,"encodeXML");
		for ( String ct : ContentType.XML.getContentTypeStrings() )
			encoders.put( ct, encClosure );
		encoders.put( ContentType.HTML.toString(), encClosure );
		
		encClosure = new MethodClosure(this,"encodeJSON");
		for ( String ct : ContentType.JSON.getContentTypeStrings() )
			encoders.put( ct, encClosure );
		
		return encoders;
	}
	
	/** 
	 * Retrieve a encoder for the given content-type.  This
	 * is called by HTTPBuilder to retrieve the correct encoder for a given 
	 * content-type.  The encoder is then used to serialize the request data 
	 * in the request body. 
	 * @param contentType
	 * @return encoder that can interpret the given content type,
	 *   or null.
	 */
	public Closure getAt( Object contentType ) {
		String ct = contentType.toString();
		int idx = ct.indexOf( ';' ); 
		if ( idx > 0 ) ct = ct.substring( 0, idx );
		
		return registeredEncoders.get(ct);
	}
	
	/** 
	 * Register a new encoder for the given content type.  If any encoder 
	 * previously existed for that content type it will be replaced.  The 
	 * closure must return an {@link HttpEntity}.  It will also usually 
	 * accept a single argument, which will be whatever is set in the request
	 * configuration closure via {@link RequestConfigDelegate#setBody(Object)}.
	 * @param contentType
	 * @param closure
	 */
	public void putAt( Object contentType, Closure value ) {
		if ( contentType instanceof ContentType ) {
			for ( String ct : ((ContentType)contentType).getContentTypeStrings() )
				this.registeredEncoders.put( ct, value );
		}
		else this.registeredEncoders.put( contentType.toString(), value );
	}
	
	/**
	 * Alias for {@link #getAt(Object)} to allow property-style access.
	 * @param key
	 * @return
	 */
	public Closure propertyMissing( Object key ) {
		return this.getAt( key );
	}
	
	/**
	 * Alias for {@link #putAt(Object, Closure)} to allow property-style access.
	 * @param key
	 * @param value
	 */
	public void propertyMissing( Object key, Closure value ) {
		this.putAt( key, value );
	}
	
	/**
	 * Iterate over the entire parser map
	 * @return
	 */
	public Iterator<Map.Entry<String,Closure>> iterator() { 
		return this.registeredEncoders.entrySet().iterator(); 
	}
}
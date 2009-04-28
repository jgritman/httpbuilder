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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderIterator;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

/**
 * <p>This class provides a simplified API similar to {@link HTTPBuilder}, but 
 * uses {@link java.net.HttpURLConnection} for I/O so that it is compatible 
 * with Google App Engine.  Features:
 * <ul>
 *  <li>Parser and Encoder support</li>
 *  <li>Easy request and response header manipulation</li>
 *  <li>Basic authentication</li>
 * </ul>
 * Notably absent are status-code based response handling and the more complex 
 * authentication mechanisms.</p>
 * 
 * TODO request encoding support?
 *
 * @see http://code.google.com/appengine/docs/java/urlfetch/overview.html
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 * @since 0.5.0
 */
public class HttpURLClient {

	private Map<String,String> defaultHeaders = new HashMap<String,String>();
	private EncoderRegistry encoderRegistry = new EncoderRegistry();
	private ParserRegistry parserRegistry = new ParserRegistry();
	private Object contentType = ContentType.ANY;
	private Object requestContentType = null;
	private URIBuilder defaultURL = null;
	
	protected Log log =  LogFactory.getLog( getClass() );
	
	/**
	 * Perform a request.  Parameters are:
	 * <dl>
	 *   <dt>url</dt><dd>the entire request URL</dd>
	 *   <dt>path</dt><dd>the path portion of the request URL, if a default
	 *     URL is set on this instance.</dd>
	 *   <dt>query</dt><dd>URL query parameters for this request.</dd>
	 *   <dt>timeout</dt><dd>see {@link HttpURLConnection#setReadTimeout(int)}</dd>
	 *   <dt>method</dt><dd>This defaults to GET, or POST if a <code>body</code> 
	 *   parameter is also specified.</dd>
	 *   <dt>contentType</dt><dd>Explicitly specify how to parse the response.
	 *     If this value is ContentType.ANY, the response <code>Content-Type</code>
	 *     header is used to determine how to parse the response.</dd>
	 *   <dt>requestContentType</dt><dd>used in a PUT or POST request to 
	 *     transform the request body and set the proper 
	 *     <code>Content-Type</code> header.  This defaults to the 
	 *     <code>contentType</code> if unset.</dd>
	 *   <dt>auth</dt><dd>Basic authorization; pass the value as a list in the 
	 *   form [user, pass]</dd>
	 *   <dt>headers</dt><dd>additional request headers, as a map</dd>
	 *   <dt>body</dt><dd>request content body, for a PUT or POST request.  
	 *     This will be encoded using the requestContentType</dd>
	 * </dl>
	 * @param args named parameters
	 * @return the parsed response
	 * @throws URISyntaxException
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	public HttpResponseDecorator request( Map<String,?> args ) 
			throws URISyntaxException, MalformedURLException, IOException {
		
		Object arg = args.remove( "url" );
		URIBuilder url = arg != null ? new URIBuilder( arg.toString() ) : defaultURL.clone();

		arg = null;
		arg = args.remove( "path" );
		if ( arg != null ) url.setPath( arg.toString() );
		arg = null;
		arg = args.remove( "query" );
		if ( arg != null ) {
			if ( ! ( arg instanceof Map ) ) 
				throw new IllegalArgumentException( "'query' must be a map" ); 
			url.setQuery( (Map<?,?>)arg );
		}
		
		HttpURLConnection conn = (HttpURLConnection)url.toURL().openConnection();
		
		arg = null;
		arg = args.remove( "timeout" );
		if ( arg != null ) 
			conn.setConnectTimeout( Integer.parseInt( arg.toString() ) );
		
		arg = null;
		arg = args.remove( "method" );
		if ( arg != null ) conn.setRequestMethod( arg.toString() );

		arg = null;
		arg = args.remove( "contentType" );
		Object contentType = arg != null ? arg : this.contentType;
		if ( contentType instanceof ContentType ) conn.addRequestProperty( 
				"Accept", ((ContentType)contentType).getAcceptHeader() );
		
		arg = null;
		arg = args.remove( "requestContentType" );
		String requestContentType = arg != null ? arg.toString() : 	
				this.requestContentType != null ? this.requestContentType.toString() : 
					contentType != null ? contentType.toString() : null;
					
		// must add default headers before setting auth:
		for ( String key : defaultHeaders.keySet() )
			conn.addRequestProperty( key, defaultHeaders.get( key ) );
		
		arg = null;
		arg = args.remove( "auth" );
		if ( arg != null ) {
			try { 
				List<?> vals = (List<?>)arg;
				conn.addRequestProperty( "Authorization", getBasicAuthHeader( 
						vals.get(0).toString(), vals.get(1).toString() ) );
			} catch ( Exception ex ) {
				throw new IllegalArgumentException( 
						"Auth argument must be a list in the form [user,auth]" );
			}
		}
					
		arg = null;
		arg = args.remove( "headers" );
		if ( arg != null ) {
			if ( ! ( arg instanceof Map ) ) 
				throw new IllegalArgumentException( "'headers' must be a map" ); 
			Map<?,?> headers = (Map<?,?>)arg;
			for ( Object key : headers.keySet() ) conn.addRequestProperty( 
					key.toString(), headers.get( key ).toString() );
		}
		
		arg = null;
		arg = args.remove( "body" );
		if ( arg != null ) {
			conn.setDoOutput( true );
			HttpEntity body = (HttpEntity)encoderRegistry.get( 
					requestContentType ).call( arg );
			// TODO configurable request charset
			
			//TODO don't override if there is a 'content-type' in the headers list
			conn.addRequestProperty( "Content-Type", requestContentType );
			try {
				DefaultGroovyMethods.leftShift( conn.getOutputStream(), 
						body.getContent() );
			}
			finally { conn.getOutputStream().close(); }
		}
		if ( args.size() > 0 ) for ( Object k : args.keySet() ) 
			log.warn( "request() : Unkown named parameter '" + k + "'" );
		
		log.debug( conn.getRequestMethod() + " " + url );
		
		HttpResponse response = new HttpURLResponseAdapter(conn);
		if ( ContentType.ANY.equals( contentType ) ) contentType = conn.getContentType();

		String method = conn.getRequestMethod();
		Object result = method.equals( "HEAD" ) || method.equals( "OPTIONS" ) ?
				null : parserRegistry.get( contentType ).call( response );
		
		log.debug( response.getStatusLine() );
		HttpResponseDecorator decoratedResponse = new HttpResponseDecorator( response, result );
		if ( log.isTraceEnabled() ) {
			System.out.println("Debug headers:");
			for ( Header h : decoratedResponse.getHeaders() )
				log.trace( "<< " + h.getName() + " : " + h.getValue() );
		}
				
		if ( conn.getResponseCode() > 399 ) 
			throw new HttpResponseException( decoratedResponse );
		
		return decoratedResponse;
	}
	
	private String getBasicAuthHeader( String user, String pass ) throws UnsupportedEncodingException {
	  return "Basic " + DefaultGroovyMethods.encodeBase64( 
			  (user + ":" + pass).getBytes("ISO-8859-1") ).toString();
	}
	
	/**
	 * Set basic user and password authorization to be used for every request.
	 * @param user
	 * @param pass
	 * @throws UnsupportedEncodingException
	 */
	public void setBasicAuth( Object user, Object pass ) throws UnsupportedEncodingException {
		this.defaultHeaders.put( "Authorization", 
				getBasicAuthHeader( user.toString(), pass.toString() ) );
	}
	
	/**
	 * The default URL for this request.  This is a {@link URIBuilder} which can 
	 * be used to easily manipulate portions of the request URL.
	 * @return
	 */
	public Object getUrl() { return this.defaultURL; }
	
	/**
	 * Set the default request URL.
	 * @see URIBuilder#convertToURI(Object)
	 * @param url any object whose <code>toString()</code> produces a valid URI.
	 * @throws URISyntaxException
	 */
	public void setUrl( Object url ) throws URISyntaxException {
		this.defaultURL = new URIBuilder( URIBuilder.convertToURI( url ) );
	}
	
	class HttpURLResponseAdapter implements HttpResponse {

		HttpURLConnection conn;
		Header[] headers;
		
		HttpURLResponseAdapter( HttpURLConnection conn ) {
			this.conn = conn;
		}
		
		@Override public HttpEntity getEntity() {
			return new HttpEntity() {

				@Override public void consumeContent() throws IOException {
					conn.getInputStream().close();
				}

				@Override public InputStream getContent() 
						throws IOException, IllegalStateException {
					if ( Status.find( conn.getResponseCode() ) 
							== Status.FAILURE ) return conn.getErrorStream(); 
					return conn.getInputStream();
				}

				@Override public Header getContentEncoding() {
					return new BasicHeader( "Content-Encoding", 
							conn.getContentEncoding() );
				}

				@Override public long getContentLength() {
					return conn.getContentLength();
				}

				@Override public Header getContentType() {
					return new BasicHeader( "Content-Type", conn.getContentType() );
				}

				@Override public boolean isChunked() {
					String enc = conn.getHeaderField( "Transfer-Encoding" );
					return enc != null && enc.contains( "chunked" );
				}

				@Override public boolean isRepeatable() {
					return false;
				}

				@Override public boolean isStreaming() {
					return true;
				}

				@Override
				public void writeTo( OutputStream out ) throws IOException {
					DefaultGroovyMethods.leftShift( out, conn.getInputStream() );
				}
				
			};
		}

		@Override
		public Locale getLocale() {  //TODO test me
			String val = conn.getHeaderField( "Locale" );
			return val != null ? new Locale( val ) : Locale.getDefault();
		}

		@Override
		public StatusLine getStatusLine() {
			try {
				return new BasicStatusLine( this.getProtocolVersion(), 
					conn.getResponseCode(), conn.getResponseMessage() );
			} catch ( IOException ex ) {
				throw new RuntimeException( "Error reading status line", ex );
			}
		}

		@Override public boolean containsHeader( String key ) {
			return conn.getHeaderField( key ) != null;
		}

		@Override public Header[] getAllHeaders() {
			if ( this.headers != null ) return this.headers;
			List<Header> headers = new ArrayList<Header>();

			// see http://java.sun.com/j2se/1.5.0/docs/api/java/net/HttpURLConnection.html#getHeaderFieldKey(int)
			int i= conn.getHeaderFieldKey( 0 ) != null ? 0 : 1;
			String key;
			while ( ( key = conn.getHeaderFieldKey( i ) ) != null ) {
				headers.add( new BasicHeader( key, conn.getHeaderField( i++ ) ) );	
			}

			this.headers = headers.toArray( new Header[headers.size()] ); 
			return this.headers;
		}

		@Override public Header getFirstHeader( String key ) {
			for ( Header h : getAllHeaders() )
				if ( h.getName().equals( key ) ) return h;
			return null;
		}

		/**
		 * HttpURLConnection does not support multiple headers of the same 
		 * name.
		 */
		@Override public Header[] getHeaders( String key ) {
			List<Header> headers = new ArrayList<Header>();
			for ( Header h : getAllHeaders() ) 
				if ( h.getName().equals( key ) ) headers.add( h );
			return headers.toArray( new Header[headers.size()] );
		}

		/**
		 * @see URLConnection#getHeaderField(String)
		 */
		@Override public Header getLastHeader( String key ) {
			String val = conn.getHeaderField( key );
			return val != null ? new BasicHeader( key, val ) : null;
		}

		@Override public HttpParams getParams() { return null; }

		@Override
		public ProtocolVersion getProtocolVersion() { 
			/* TODO this could potentially cause problems if the server is 
			   using HTTP 1.0 */
			return new ProtocolVersion( "HTTP", 1, 1 );
		}

		@Override
		public HeaderIterator headerIterator() {
			return new BasicHeaderIterator( this.getAllHeaders(), null );
		}

		@Override
		public HeaderIterator headerIterator( String key ) {
			return new BasicHeaderIterator( this.getHeaders( key ), key );
		}

		@Override public void setEntity( HttpEntity entity ) {}
		@Override public void setLocale( Locale l ) {}
		@Override public void setReasonPhrase( String phrase ) {}
		@Override public void setStatusCode( int code ) {}
		@Override public void setStatusLine( StatusLine line ) {}
		@Override public void setStatusLine( ProtocolVersion v, int code ) {}
		@Override public void setStatusLine( ProtocolVersion arg0, 
				int arg1, String arg2 ) {}
		@Override public void addHeader( Header arg0 ) {}
		@Override public void addHeader( String arg0, String arg1 ) {}
		@Override public void removeHeader( Header arg0 ) {}
		@Override public void removeHeaders( String arg0 ) {}
		@Override public void setHeader( Header arg0 ) {}
		@Override public void setHeader( String arg0, String arg1 ) {}
		@Override public void setHeaders( Header[] arg0 ) {}
		@Override public void setParams( HttpParams arg0 ) {}		
	}

	public Map<String,String> getHeaders() {
		return defaultHeaders;
	}

	public void setHeaders( Map<?,?> headers ) {
		this.defaultHeaders.clear();
		for ( Object key : headers.keySet() ) {
			Object val = headers.get( key );
			if ( val != null ) this.defaultHeaders.put( 
					key.toString(), val.toString() );
		}
	}

	public EncoderRegistry getEncoderRegistry() {
		return encoderRegistry;
	}

	public void setEncoderRegistry( EncoderRegistry encoderRegistry ) {
		this.encoderRegistry = encoderRegistry;
	}

	public ParserRegistry getParserRegistry() {
		return parserRegistry;
	}

	public void setParserRegistry( ParserRegistry parserRegistry ) {
		this.parserRegistry = parserRegistry;
	}

	public Object getContentType() {
		return contentType;
	}

	public void setContentType( Object ct ) {
		this.contentType = (ct == null) ? ContentType.ANY : ct;
	}

	public Object getRequestContentType() {
		return requestContentType;
	}

	public void setRequestContentType( Object requestContentType ) {
		this.requestContentType = requestContentType;
	}
}

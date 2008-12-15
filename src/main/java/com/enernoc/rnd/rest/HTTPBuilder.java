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
package com.enernoc.rnd.rest;

import groovy.lang.Closure;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.MethodClosure;

import static com.enernoc.rnd.rest.URIBuilder.convertToURI;

/** <p>
 * Groovy DSL for easily making HTTP requests, and handling request and response
 * data.  This class adds a number of convenience mechanisms built on top of 
 * Apache HTTPClient for things like URL-encoded POSTs and REST requests that 
 * require building and parsing JSON or XML.  Convenient access to a few common
 * authentication methods is also available.</p>
 * 
 * <h3>Examples:</h3>
 * Perform an HTTP GET and print the response:
 * <pre>
 *   def http = new HTTPBuilder('http://www.google.com')
 *   
 *   http.get( path:'/search', query:[q:'Groovy'] ) { resp, reader ->
 *     println "response status: ${resp.statusLine}"
 *     println 'Response data: -----'
 *     System.out << reader
 *     println '\n--------------------'
 *   }
 * </pre>
 *   
 * Long form for other HTTP methods, and response-code-specific handlers:
 *   
 * <pre>
 *   http.request(GET,TEXT) { req ->
 *     response.success = { resp, stream -> // single closure is for all responses
 *       println 'my response handler!'
 *       assert resp.statusLine.statusCode == 200
 *       println resp.statusLine
 *       //System.out << stream // print response stream
 *     }
 *     
 *     response.'401' = { resp ->
 *       println 'access denied'
 *     }
 *   }
 * </pre>
 *   
 * You can also set a default response handler called for any staus
 * code > 399 that is not matched to a specific handler. Setting the value
 * outside a request closure means it will apply to all future requests with
 * this HTTPBuilder instance:
 * <pre>
 *   http.handler.failure = { resp ->
 *     println "Unexpected failure: ${resp.statusLine}"
 *   }
 * </pre>
 *   
 *   
 * And...  Automatic response parsing for registered content types!
 *   
 * <pre>
 *   http.request( 'http://ajax.googleapis.com', GET, JSON ) {
 *     url.path = '/ajax/services/search/web'
 *     url.query = [ v:'1.0', q: 'Calvin and Hobbes' ]
 *     
 *     response.success = { resp, json ->
 *       assert json.size() == 3
 *       println "Query response: "
 *       json.responseData.results.each {
 *         println "  ${it.titleNoFormatting} : ${it.visibleUrl}"
 *       }
 *     }
 *   }
 * </pre>
 * 
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 */
public class HTTPBuilder {
	
	protected AbstractHttpClient client = new DefaultHttpClient();	
	protected URI defaultURI = null; // TODO make this a URIBuilder?
	protected AuthConfig auth = new AuthConfig( this );
	
	protected Object defaultContentType = ContentType.TEXT;
	protected final Map<String,Closure> defaultResponseHandlers = buildDefaultResponseHandlers();
	protected ContentEncodingRegistry contentEncodingHandler = new ContentEncodingRegistry();
	
	protected final Map<String,String> defaultRequestHeaders = new HashMap<String,String>();
	
	protected EncoderRegistry encoders = new EncoderRegistry();
	protected ParserRegistry parsers = new ParserRegistry();
	
	public HTTPBuilder() { 
		super();
		this.setContentEncoding( ContentEncoding.Type.GZIP, 
				ContentEncoding.Type.DEFLATE );
	}
	
	/**
	 * Give a default URL to be used for all request methods that don't 
	 * explicitly take a URL parameter.
	 * @param defaultURL either a {@link URL}, {@link URI} or String
	 * @throws URISyntaxException if the URL was not parse-able
	 */
	public HTTPBuilder( Object defaultURL ) throws URISyntaxException {
		this();
		this.defaultURI = convertToURI( defaultURL );
	}
	
	/**
	 * Give a default URL to be used for all request methods that don't 
	 * explicitly take a URL parameter, and a default content-type to be used
	 * for request encoding and response parsing.
	 * @param defaultURL either a {@link URL}, {@link URI} or String
	 * @param defaultContentType content-type string.  See {@link ContentType}
	 *   for common types.
	 * @throws URISyntaxException if the URL was not parse-able
	 */
	public HTTPBuilder( Object defaultURL, Object defaultContentType ) throws URISyntaxException {
		this();
		this.defaultURI = convertToURI( defaultURL );
		this.defaultContentType = defaultContentType; 
	}
	
	/**
	 * Convenience method to perform an HTTP GET.  The response closure will be 
	 * called only on a successful response; a 'failed' response (i.e. any 
	 * HTTP status code > 399) will be handled by the registered 'failure' 
	 * handler.  The {@link #defaultFailureHandler(HttpResponse) default 
	 * failure handler} throws an {@link HttpResponseException}
	 * @param args see {@link SendDelegate#setPropertiesFromMap(Map)}
	 * @param responseClosure code to handle a successful HTTP response
	 * @return any value returned by the response closure.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Object get( Map<String,?> args, Closure responseClosure ) 
			throws ClientProtocolException, IOException, URISyntaxException {
		SendDelegate delegate = new SendDelegate( new HttpGet(),
				this.defaultContentType,
				this.defaultRequestHeaders,
				this.defaultResponseHandlers );
		
		delegate.setPropertiesFromMap( args );		
		delegate.getResponse().put( Status.SUCCESS.toString(), responseClosure );
		return this.doRequest( delegate );
	}
	
	/** <p>
	 * Convenience method to perform an HTTP form POST.  The response closure will be 
	 * called only on a successful response; a 'failed' response (i.e. any 
	 * HTTP status code > 399) will be handled by the registered 'failure' 
	 * handler.  The {@link #defaultFailureHandler(HttpResponse) default 
	 * failure handler} throws an {@link HttpResponseException}.</p>  
	 * 
	 * <p>The request body (specified by a <code>body</code> named parameter) 
	 * will be converted to a url-encoded form string unless a different 
	 * <code>requestContentType</code> named parameter is passed to this method.
	 *  (See {@link EncoderRegistry#encodeForm(Map)}.) </p>
	 * 
	 * @param args see {@link SendDelegate#setPropertiesFromMap(Map)}
	 * @param responseClosure code to handle a successful HTTP response
	 * @return any value returned by the response closure.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Object post( Map<String,?> args, Closure responseClosure ) 
			throws URISyntaxException, ClientProtocolException, IOException {
		SendDelegate delegate = new SendDelegate( new HttpPost(),
				this.defaultContentType, 
				this.defaultRequestHeaders,
				this.defaultResponseHandlers );
		
		/* by default assume the request body will be URLEncoded, but allow
		   the 'requestContentType' named argument to override this if it is 
		   given */ 
		delegate.setRequestContentType( ContentType.URLENC.toString() );
		delegate.setPropertiesFromMap( args );		
		delegate.getResponse().put( Status.SUCCESS.toString(), responseClosure );

		return this.doRequest( delegate );
	}
	
	public Object request( Method m, Closure configClosure ) throws ClientProtocolException, IOException {
		return this.doRequest( this.defaultURI, m, this.defaultContentType, configClosure );
	}

	public Object request( Method m, Object contentType, Closure configClosure ) 
			throws ClientProtocolException, IOException {
		return this.doRequest( this.defaultURI, m, contentType, configClosure );
	}

	/**
	 * Make a request for the given HTTP method and content-type, with 
	 * additional options configured in the <code>configClosure</code>.  See
	 * {@link SendDelegate} for options.
	 * @param uri either a URI, URL, or String
	 * @param method {@link Method HTTP method}
	 * @param contentType either a {@link ContentType} or valid content-type string.
	 * @param configClosure closure from which to configure options like 
	 *   {@link SendDelegate#setPath(String) path}, 
	 *   {@link SendDelegate#setParams(Map) request parameters}, 
	 *   {@link SendDelegate#setHeaders(Map) headers},
	 *   {@link SendDelegate#setBody(Object) request body} and
	 *   {@link SendDelegate#getResponse() response handlers}. 
	 *   
	 * @return whatever value was returned by the executed response handler.
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException if a URI string or URL was invalid.
	 */
	public Object request( Object uri, Method method, Object contentType, Closure configClosure ) 
			throws ClientProtocolException, IOException, URISyntaxException {
		return this.doRequest( convertToURI( uri ), method, contentType, configClosure );
	}

	protected Object doRequest( URI uri, Method method, Object contentType, Closure configClosure ) 
			throws ClientProtocolException, IOException {

		HttpRequestBase reqMethod;
		try { reqMethod = method.getRequestType().newInstance();
		// this exception should reasonably never occur:
		} catch ( Exception e ) { throw new RuntimeException( e ); }

		reqMethod.setURI( uri );
		SendDelegate delegate = new SendDelegate( reqMethod, contentType, 
				this.defaultRequestHeaders,
				this.defaultResponseHandlers );		
		configClosure.setDelegate( delegate );
		configClosure.call( client );		

		return this.doRequest( delegate );
	}
	
	protected Object doRequest( SendDelegate delegate ) throws ClientProtocolException, IOException {
		
		HttpRequestBase reqMethod = delegate.getRequest();
		
		Object contentType = delegate.getContentType();
		String acceptContentTypes = contentType.toString();
		if ( contentType instanceof ContentType ) 
			acceptContentTypes = ((ContentType)contentType).getAcceptHeader();
		
		reqMethod.setHeader( "Accept", acceptContentTypes );
		reqMethod.setURI( delegate.getURL().toURI() );

		// set any request headers from the delegate
		Map<String,String> headers = delegate.getHeaders(); 
		for ( String key : headers.keySet() ) reqMethod.setHeader( key, headers.get( key ) );
		
		HttpResponse resp = client.execute( reqMethod );
		Closure responseClosure = delegate.findResponseHandler( 
				resp.getStatusLine().getStatusCode() );
		
		Object[] closureArgs = null;
		switch ( responseClosure.getMaximumNumberOfParameters() ) {
		case 1 :
			closureArgs = new Object[] { resp };
			break;
		case 2 :
			String responseContentType = parsers.getContentType( resp );
			Object parsedData = parsers.get( responseContentType ).call( resp );			
			closureArgs = new Object[] { resp, parsedData };
			break;
		default:
			throw new IllegalArgumentException( 
					"Response closure must accept one or two parameters" );
		}
		
		Object returnVal = responseClosure.call( closureArgs );
		if ( resp.getEntity().isStreaming() ) resp.getEntity().consumeContent();
		return returnVal;
	}
	
	protected Map<String,Closure> buildDefaultResponseHandlers() {
		Map<String,Closure> map = new HashMap<String, Closure>();
		map.put( Status.SUCCESS.toString(), 
				new MethodClosure(this,"defaultSuccessHandler"));
		map.put(  Status.FAILURE.toString(),
				new MethodClosure(this,"defaultFailureHandler"));
		
		return map;
	}

	/**
	 * This is the default <code>response.success</code> handler.  It will be 
	 * executed if no status-code-specific handler is set (i.e. 
	 * <code>response.'200'= {..}</code>).  This simply prints the status line 
	 * and the response stream to <code>System.out</code>.  In most cases you
	 * will want to define a <code>response.success = {...}</code> handler from 
	 * the request closure, which will replace this method.   
	 *  
	 * @param resp
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	protected void defaultSuccessHandler( HttpResponse resp ) throws IllegalStateException, IOException {
		System.out.println( resp.getStatusLine() );
		System.out.println( DefaultGroovyMethods.getText( resp.getEntity().getContent() ) ); 
	}
	
	/**
	 * This is the default <code>response.failure</code> handler.  It will be 
	 * executed if no status-code-specific handler is set (i.e. 
	 * <code>response.'404'= {..}</code>).  This default handler will throw a 
	 * {@link HttpResponseException} when executed.  In most cases you
	 * will want to define your own <code>response.failure = {...}</code> 
	 * handler from the request closure, if you don't want an exception to be 
	 * thrown for a 4xx and 5xx status response.   

	 * @param resp
	 * @throws HttpResponseException
	 */
	protected void defaultFailureHandler( HttpResponse resp ) throws HttpResponseException {
		throw new HttpResponseException( resp.getStatusLine().getStatusCode(), 
				resp.getStatusLine().getReasonPhrase() );
	}
	
	/**
	 * Retrieve the map of response code handlers.  Each map key is a response 
	 * code as a string (i.e. '401') or either 'success' or 'failure'.  Use this
	 * to set default response handlers, e.g.
	 * <pre>builder.handler.'401' = { resp -> println "${resp.statusLine}" }</pre>
	 * @see Status 
	 * @return
	 */
	public Map<String,Closure> getHandler() {
		return this.defaultResponseHandlers;
	}
	
	/**
	 * Retrieve the map of registered response content-type parsers.  Use 
	 * this to set default response parsers, e.g.
	 * <pre>
	 * builder.parser.'text/javascript' = { resp -> 
	 * 	  return resp.entity.content // just returns an InputStream
	 * }</pre>  
	 * @return
	 */
	public Map<String,Closure> getParser() {
		return this.parsers.registeredParsers;
	}
	
	/**
	 * Retrieve the map of registered request content-type encoders.  Use this
	 * to set a default request encoder, e.g.
	 * <pre>
	 * builder.encoder.'text/javascript' = { body ->
	 *   def json = body.call( new JsonGroovyBuilder() )
	 *   return new StringEntity( json.toString() )
	 * } 
	 * @return
	 */
	public Map<String,Closure> getEncoder() {
		return this.encoders.registeredEncoders;
	}
	
	/**
	 * Set the default content type that will be used to select the appropriate
	 * request encoder and response parser.  The {@link ContentType} enum holds
	 * some common content-types that may be used, i.e. <pre>
	 * import static ContentType.*
	 * builder.contentType = XML
	 * </pre> 
	 * @see EncoderRegistry
	 * @see ParserRegistry
	 * @param ct either a {@link ContentType} or string value (i.e. <code>"text/xml"</code>.)
	 */
	public void setContentType( Object ct ) {
		this.defaultContentType = ct;
	}
	
	
	/**
	 * Set acceptable request and response content-encodings. 
	 * @see ContentEncodingRegistry
	 * @param encodings each Object should be either a 
	 * {@link ContentEncoding.Type} value, or a <code>content-encoding</code> 
	 * string that is known by the {@link ContentEncodingRegistry}
	 */
	public void setContentEncoding( Object... encodings ) {
		this.contentEncodingHandler.setInterceptors( client, encodings );
	}
	
	/**
	 * Set the default URL used for requests that do not explicitly take a 
	 * <code>url</code> param.  
	 * @param url a URL, URI, or String
	 * @throws URISyntaxException
	 */
	public void setURL( Object url ) throws URISyntaxException {
		this.defaultURI = convertToURI( url );
	}
	
	/**
	 * Get the default URL used for requests that do not explicitly take a 
	 * <code>url</code> param.
	 * @return url a {@link URL} instance.  Note that the return type is Object
	 * simply so that it matches with its JavaBean {@link #setURL(Object)} 
	 * counterpart.
	 */
	public Object getURL() {
		try {
			return defaultURI.toURL();
		} catch ( MalformedURLException e ) {
			throw new RuntimeException( e );
		}
	}

	/**
	 * Set the default headers to add to all requests made by this builder 
	 * instance.  These values will replace any previously set default headers.
	 * @param headers map of header names & values.
	 */
	public void setHeaders( Map<?,?> headers ) {
		this.defaultRequestHeaders.clear();
		if ( headers == null ) return;
		for( Object key : headers.keySet() ) {
			Object val = headers.get( key );
			if ( val == null ) continue;
			this.defaultRequestHeaders.put( key.toString(), val.toString() );
		}
	}
	
	/**
	 * Get the map of default headers that will be added to all requests.
	 * This is a 'live' collection so it may be used to add or remove default 
	 * values. 
	 * @return the map of default header names and values.
	 */
	public Map<String,String> getHeaders() {
		return this.defaultRequestHeaders;
	}

	/**
	 * Return the underlying HTTPClient that is used to handle HTTP requests.
	 * @return the client instance.
	 */
	public AbstractHttpClient getClient() { return this.client; }
	
	/**
	 * Used to access the {@link AuthConfig} handler used to configure common 
	 * authentication mechanism.  Example:
	 * <pre>builder.auth.basic( 'myUser', 'somePassword' )</pre>
	 * @return
	 */
	public AuthConfig getAuth() { return this.auth; }
	
	/**
	 * Set an alternative {@link AuthConfig} implementation to handle 
	 * authorization.
	 * @param ac instance to use. 
	 */
	public void setAuthConfig( AuthConfig ac ) {
		this.auth = ac;
	}
	
	/**
	 * Set a custom registry used to handle different request 
	 * <code>content-type</code>s.
	 * @param er
	 */
	public void setEncoderRegistry( EncoderRegistry er ) {
		this.encoders = er;
	}
	
	/**
	 * Set a custom registry used to handle different response 
	 * <code>content-type</code>s
	 * @param pr
	 */
	public void setParserRegistry( ParserRegistry pr ) {
		this.parsers = pr;
	}
	
	/**
	 * Set a custom registry used to handle different 
	 * <code>content-encoding</code> types in responses.  
	 * @param cer
	 */
	public void setContentEncodingRegistry( ContentEncodingRegistry cer ) {
		this.contentEncodingHandler = cer;
	}
	
	
	
	/**
	 * Encloses all properties and method calls used within the 
	 * {@link HTTPBuilder#request(URI, Method, ContentType, Closure)} 'config' 
	 * closure argument. 
	 */
	protected class SendDelegate {
		protected HttpRequestBase request;
		protected Object contentType;
		protected String requestContentType;
		protected Map<String,Closure> responseHandlers = new HashMap<String,Closure>();
		protected URIBuilder url;
		protected Map<String,String> headers = new HashMap<String,String>();
		
		public SendDelegate( HttpRequestBase request, Object contentType, 
				Map<String,String> defaultRequestHeaders,
				Map<String,Closure> defaultResponseHandlers ) {
			this.request = request;
			this.headers.putAll( defaultRequestHeaders );
			this.contentType = contentType;
			this.responseHandlers.putAll( defaultResponseHandlers );
			this.url = new URIBuilder(request.getURI());
		}
		
		/** 
		 * Use this object to manipulate parts of the request URL, like 
		 * query params and request path.  Example:
		 * <pre>
		 * builder.request(GET,XML) {
		 *   url.path = '../other/request.jsp'
		 *   url.params = [p1:1, p2:2]
		 *   ...
		 * }</pre>
		 * @return {@link URIBuilder} to manipulate the request URL 
		 */
		public URIBuilder getURL() { return this.url; }

		protected HttpRequestBase getRequest() { return this.request; }
		
		/**
		 * Get the content-type of any data sent in the request body and the 
		 * expected response content-type.
		 * @return whatever value was assigned via {@link #setContentType(Object)}
		 * or passed from the {@link HTTPBuilder#defaultContentType} when this
		 * SendDelegateinstance was constructed.
		 */
		protected Object getContentType() { return this.contentType; }
		
		/**
		 * Set the content-type used for any data in the request body, as well
		 * as the <code>Accept</code> content-type that will be used for parsing
		 * the response. The value should be either a {@link ContentType} value 
		 * or a String, i.e. <code>"text/plain"</code>
		 * @param ct content-type to send and recieve content
		 */
		protected void setContentType( Object ct ) {
			if ( ct == null ) this.contentType = defaultContentType;
			this.contentType = ct; 
		}
		
		/**
		 * The request content-type, if different from the {@link #contentType}.
		 * @return
		 */
		protected String getRequestContentType() {
			if ( this.requestContentType != null ) return this.requestContentType;
			else return this.getContentType().toString();
		}
		
		/**
		 * Assign a different content-type for the request than is expected for 
		 * the response.  This is useful if i.e. you want to post URL-encoded
		 * form data but expect the response to be XML or HTML.  The 
		 * {@link #getContentType()} will always control the <code>Accept</code>
		 * header, and will be used for the request content <i>unless</i> this 
		 * value is also explicitly set.
		 * @param ct either a {@link ContentType} value or a valid content-type
		 * String.
		 */
		protected void setRequestContentType( String ct ) { 
			this.requestContentType = ct; 
		}
		
		/**
		 * Valid arguments:
		 * <dl>
		 *   <dt>url</dt><dd>Either a URI, URL, or String. 
		 *   	If not supplied, the HTTPBuilder's default URL is used.</dd>
		 *   <dt>path</dt><dd>Request path that is merged with the URL</dd>
		 *   <dt>params</dt><dd>Map of request parameters</dd>
		 *   <dt>headers</dt><dd>Map of HTTP headers</dd>
		 *   <dt>contentType</dt><dd>Request content type and Accept header.  
		 *   	If not supplied, the HTTPBuilder's default content-type is used.</dd>
		 *   <dt>requestContentType</dt><dd>content type for the request, if it
		 *      is different from the expected response content-type</dd>
		 *   <dt>body</dt><dd>Request body that will be encoded based on the given contentType</dd>
		 * </dl>
		 * @param args named parameters to set properties on this delegate.
		 * @throws MalformedURLException
		 * @throws URISyntaxException
		 */
		@SuppressWarnings("unchecked")
		protected void setPropertiesFromMap( Map<String,?> args ) throws MalformedURLException, URISyntaxException {
			Object uri = args.get( "url" );
			if ( uri == null ) uri = defaultURI;
			url = new URIBuilder( convertToURI( uri ) );
			
			Map params = (Map)args.get( "params" );
			if ( params != null ) this.url.setQuery( params );
			Map headers = (Map)args.get( "headers" );
			if ( headers != null ) this.setHeaders( headers );
			
			String path = (String)args.get( "path" );
			if ( path != null ) this.url.setPath( path );
			
			Object contentType = args.get( "contentType" );
			if ( contentType != null ) this.setContentType( contentType );
			
			contentType = args.get( "requestContentType" );
			if ( contentType != null ) this.setRequestContentType( contentType.toString() );
			
			Object body = args.get("body");
			if ( body != null ) this.setBody( body );
		}

		/**
		 * Set request headers.  These values will be <strong>merged</strong>
		 * with any {@link HTTPBuilder#getHeaders() default request headers.} 
		 * (The assumption is you'll probably want to add a bunch of headers to 
		 * whatever defaults you've already set).  If you <i>only</i> want to 
		 * use values set here, simply call {@link #getHeaders() headers.clear()}
		 * first.
		 */
		public void setHeaders( Map<?,?> newHeaders ) {
			for( Object key : newHeaders.keySet() ) {
				Object val = newHeaders.get( key );
				if ( val == null ) this.headers.remove( key );
				else this.headers.put( key.toString(), val.toString() );
			}
		}
		
		/**
		 * Get request headers (including any default headers).  Note that this
		 * will not include any <code>Accept</code>, <code>Content-Type</code>,
		 * or <code>Content-Encoding</code> headers that are automatically
		 * handled by any encoder or parsers in effect.  Note that any values 
		 * set here <i>will</i> override any of those automatically assigned 
		 * values.
		 * header that is a
		 * @return
		 */
		public Map<String,String> getHeaders() {
			return this.headers;
		}
		
		/**
		 * Convenience method to set a request content-type at the same time
		 * the request body is set.  This is a variation of 
		 * {@link #setBody(Object)} that allows for a different content-type
		 * than what is expected for the response.  
		 * 
		 * <p>Example:	
		 * <pre>
		 * http.request(POST,HTML) {
		 *   
		 *   /* request data is interpreted as a JsonBuilder closure in the 
		 *      default EncoderRegistry implementation * /
		 *   send( 'text/javascript' ) {  
		 *     a : ['one','two','three']
		 *   }
		 *   
		 *   // response content-type is what was specified in the outer request() argument:
		 *   response.success = { resp, html -> 
		 *   
		 *   }
		 * }
		 * </pre>
		 * @param contentType either a {@link ContentType} or content-type 
		 * 	string like <code>"text/xml"</code>
		 * @param requestBody
		 */
		public void send( Object contentType, Object requestBody ) {
			this.setRequestContentType( contentType.toString() );
			this.setBody( requestBody );
		}

		/**
		 * Set the request body.  This value may be of any type supported by 
		 * the associated {@link EncoderRegistry request encoder}.  
		 * @see #send(Object, Object)
		 * @param body data or closure interpretes as the request body
		 */
		public void setBody( Object body ) {
			if ( ! (request instanceof HttpEntityEnclosingRequest ) )
				throw new UnsupportedOperationException( 
						"Cannot set a request body for a " + request.getMethod() + " method" );
			Closure encoder = encoders.get( this.getRequestContentType() );
			HttpEntity entity = (HttpEntity)encoder.call( body );
			
			((HttpEntityEnclosingRequest)request).setEntity( entity );
		}
		
		/**
		 * Get the proper response handler for the response code.  This is called
		 * by the {@link HTTPBuilder} class in order to find the proper handler
		 * based on the response status code.
		 *  
		 * @param status
		 * @param delegate
		 * @return the response handler
		 */
		protected Closure findResponseHandler( int statusCode ) {
			Closure handler = getResponse().get( Integer.toString( statusCode ) );
			if ( handler == null ) handler = 
				getResponse().get( Status.find( statusCode ).toString() );
			return handler;
		}
		
		/**
		 * Access the response handler map to set response parsing logic.  
		 * i.e.<pre>
		 * builder.request( GET, XML ) {
		 *   response.success = { xml ->
		 *      /* for XML content type, the default parser 
		 *         will return an XmlSlurper * /
		 *   	xml.root.children().each { println it } 
		 *   }
		 * }</pre>
		 * @return
		 */
		public Map<String,Closure> getResponse() { return responseHandlers; }
	}	
}
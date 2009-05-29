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
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;

/**
 * Extension to HTTPBuilder that basically attempts to provide a slightly more
 * REST-ful face on top of HTTPBuilder.  The differences between this class
 * and HTTPBuilder are such:
 * 
 * <ul>
 *   <li>Access to response headers.  All "request" methods on this class by 
 *   default return an instance of {@link HttpResponseDecorator}, which allows for simple
 *   evaluation of the response.</li>
 *   <li>No streaming responses.  Responses are expected to either not carry data 
 * (in the case of HEAD or DELETE) or be parse-able into some sort of object.  
 *   That object is accessible via {@link HttpResponseDecorator#getData()}.</li>
 * </ul>  
 * 
 * <p>By default, all request method methods will return a {@link HttpResponseDecorator}
 * instance, which provides convenient access to response headers and the parsed
 * response body.  The response body is parsed based on content-type, identical
 * to how HTTPBuilder's {@link HTTPBuilder#defaultSuccessHandler(HttpResponse, 
 * Object) default response handler} functions.</p>
 * 
 * <p>Failed requests (i.e. responses which return a status code &gt; 399) will
 * by default throw a {@link HttpResponseException}.  This exception may be used 
 * to retrieve additional information regarding the response as well.</p>
 * 
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 * @since 0.5
 */
public class RESTClient extends HTTPBuilder {
	

	public RESTClient() {
		super();
	}
	
	/**
	 * See {@link HTTPBuilder#HTTPBuilder(Object)}
	 * @param defaultURI
	 * @throws URISyntaxException
	 */
	public RESTClient( Object defaultURI ) throws URISyntaxException {
		super( defaultURI );
	}
	
	/**
	 * See {@link HTTPBuilder#HTTPBuilder(Object, Object)}
	 * @param defaultURI
	 * @throws URISyntaxException
	 */
	public RESTClient( Object defaultURI, Object defaultContentType ) throws URISyntaxException {
		super( defaultURI, defaultContentType );
	}
	
	
	/**
	 * <p>Convenience method to perform an HTTP GET.  It will use the HTTPBuilder's
	 * {@link #getHandler() registered response handlers} to handle success or 
	 * failure status codes.  By default, the <code>success</code> response 
	 * handler will attempt to parse the data and simply return the parsed 
	 * object.</p>
	 * 
	 * <p><strong>Note:</strong> If using the {@link #defaultSuccessHandler(HttpResponse, Object)
	 * default <code>success</code> response handler}, be sure to read the 
	 * caveat regarding streaming response data.</p>
	 * 
	 * @see #getHandler()
	 * @see #defaultSuccessHandler(HttpResponse, Object)
	 * @see #defaultFailureHandler(HttpResponse)
	 * @param args see {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
	 * @return a {@link HttpResponseDecorator}, if the default response handler is not 
	 *   overridden.
	 * @throws URISyntaxException 
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 */
	public Object get( Map<String,?> args ) throws ClientProtocolException, 
			IOException, URISyntaxException {
		return super.doRequest( new RequestConfigDelegate( args, new HttpGet(), null ) );
	}
	
	/** <p>
	 * Convenience method to perform an HTTP form POST.  The response closure will be 
	 * called only on a successful response.</p>   
	 * 
	 * <p>A 'failed' response (i.e. any 
	 * HTTP status code > 399) will be handled by the registered 'failure' 
	 * handler.  The {@link #defaultFailureHandler(HttpResponse) default 
	 * failure handler} throws a {@link HttpResponseException}.</p>  
	 * 
	 * <p>The request body (specified by a <code>body</code> named parameter) 
	 * will be encoded based on the <code>requestContentType</code> named 
	 * parameter, or if none is given, the default 
	 * {@link HTTPBuilder#setContentType(Object) content-type} for this instance.
	 * </p>
	 * 
	 * @param args see {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
	 * @return a {@link HttpResponseDecorator}, if the default response handler is not 
	 *   overridden.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	@Override public Object post( Map<String,?> args ) 
			throws URISyntaxException, ClientProtocolException, IOException {
		return super.doRequest( new RequestConfigDelegate( args, new HttpPost(), null ) );
	}
	
	public Object put( Map<String,?> args ) throws URISyntaxException, 
			ClientProtocolException, IOException {
		return this.doRequest( new RequestConfigDelegate( args, new HttpPut(), null ) );
	}
	
	public Object head( Map<String,?> args ) throws URISyntaxException, 
			ClientProtocolException, IOException {
		return this.doRequest( new RequestConfigDelegate( args, new HttpHead(), null ) );
	}
	
	public Object delete( Map<String,?> args ) throws URISyntaxException, 
			ClientProtocolException, IOException {
		return this.doRequest( new RequestConfigDelegate( args, new HttpDelete(), null ) );
	}
	
	public Object options( Map<String,?> args ) throws ClientProtocolException, 
			IOException, URISyntaxException {
		return this.doRequest( new RequestConfigDelegate( args, new HttpOptions(), null ) );
	}
	
	@Override
	protected HttpResponseDecorator defaultSuccessHandler( HttpResponse resp, Object data )
			throws IOException {
		return new HttpResponseDecorator( resp, data );
	}
	
	/**
	 * Throws an exception for non-successful HTTP response codes.  The 
	 * exception instance will have a reference to the response object, in 
	 * order to inspect status code and headers within the <code>catch</code> 
	 * block.
	 * @param resp response object
	 * @param data parsed response data
	 * @throws HttpResponseException exception which can access the response 
	 *   object.
	 */
	protected void defaultFailureHandler( HttpResponse resp, Object data ) throws HttpResponseException {
		throw new HttpResponseException( new HttpResponseDecorator( resp, data ) );
	}
}
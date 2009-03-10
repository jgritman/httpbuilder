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
import org.apache.http.client.HttpResponseException;
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
 * # Access to response headers.  All "request" methods on this class by default
 * return an instance of {@link ResponseProxy}, which allows for simple 
 * evaluation of the response.

 * # No streaming responses.  Responses are expected to either not carry data 
 * (in the case of HEAD or DELETE) or be parse-able into some sort of object.  
 * that object is accessable via {@link ResponseProxy#getData()}.  
 * 
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 * @since v0.5.0
 */
public class RESTClient extends HTTPBuilder {
	

	public RESTClient() {
		super();
	}
	
	public RESTClient( Object defaultURI ) throws URISyntaxException {
		super( defaultURI );
	}
	
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
	 * @param args see {@link RequestConfigDelegate#setPropertiesFromMap(Map)}
	 * @return whatever was returned from the response closure.  
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
	 * failure handler} throws an {@link HttpResponseException}.</p>  
	 * 
	 * <p>The request body (specified by a <code>body</code> named parameter) 
	 * will be converted to a URL-encoded form string unless a different 
	 * <code>requestContentType</code> named parameter is passed to this method.
	 *  (See {@link EncoderRegistry#encodeForm(Map)}.) </p>
	 * 
	 * @param args see {@link RequestConfigDelegate#setPropertiesFromMap(Map)}
	 * @param responseClosure code to handle a successful HTTP response
	 * @return any value returned by the response closure.
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	public Object post( Map<String,?> args ) throws URISyntaxException, 
			ClientProtocolException, IOException {
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
	protected ResponseProxy defaultSuccessHandler( HttpResponse resp, Object data )
			throws IOException {
		return new ResponseProxy( resp, data );
	}
	
	/**
	 * If the user wants an exception thrown for non-successful HTTP response 
	 * codes.
	 * @param resp
	 * @param data
	 * @throws RESTResponseException
	 */
	protected void defaultFailureHandler( HttpResponse resp, Object data ) throws RESTResponseException {
		throw new RESTResponseException( new ResponseProxy( resp, data ) );
	}	
}

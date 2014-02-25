/*
 * Copyright 2008-2011 Thomas Nichols.  http://blog.thomnichols.org
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
 * of the community, you are encouraged (but not required) to donate any
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
import org.apache.http.client.methods.HttpPatch;

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
 * to how HTTPBuilder's {@link HTTPBuilder#defaultSuccessHandler(HttpResponseDecorator,
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


    /**
     * Constructor.
     * @see HTTPBuilder#HTTPBuilder()
     */
    public RESTClient() { super(); }

    /**
     * See {@link HTTPBuilder#HTTPBuilder(Object)}
     * @param defaultURI default request URI (String, URI, URL or {@link URIBuilder})
     * @throws URISyntaxException
     */
    public RESTClient( Object defaultURI ) throws URISyntaxException {
        super( defaultURI );
    }

    /**
     * See {@link HTTPBuilder#HTTPBuilder(Object, Object)}
     * @param defaultURI default request URI (String, URI, URL or {@link URIBuilder})
     * @param defaultContentType default content-type (String or {@link ContentType})
     * @throws URISyntaxException
     */
    public RESTClient( Object defaultURI, Object defaultContentType ) throws URISyntaxException {
        super( defaultURI, defaultContentType );
    }


    /**
     * <p>Convenience method to perform an HTTP GET request.  It will use the HTTPBuilder's
     * {@link #getHandler() registered response handlers} to handle success or
     * failure status codes.  By default, the
     * {@link #defaultSuccessHandler(HttpResponseDecorator, Object)}
     * <code>success</code> response handler will return a decorated response
     * object that can be used to read response headers and data.</p>
     *
     * <p>A 'failed' response (i.e. any HTTP status code > 399) will be handled
     * by the registered 'failure' handler.
     * The {@link #defaultFailureHandler(HttpResponseDecorator, Object)
     * default failure handler} throws a {@link HttpResponseException}.</p>
     *
     * @see #defaultSuccessHandler(HttpResponseDecorator, Object)
     * @see #defaultFailureHandler(HttpResponseDecorator, Object)
     * @param args named parameters - see
     *  {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
     * @return a {@link HttpResponseDecorator}, unless the default success
     *      handler is overridden.
     * @throws URISyntaxException
     * @throws IOException
     * @throws ClientProtocolException
     */
    public Object get( Map<String,?> args ) throws ClientProtocolException,
            IOException, URISyntaxException {
        return doRequest( new RequestConfigDelegate( args, new HttpGet(), null ) );
    }

    /**
     * <p>Convenience method to perform a POST request.</p>
     *
     * <p>The request body (specified by a <code>body</code> named parameter)
     * will be encoded based on the <code>requestContentType</code> named
     * parameter, or if none is given, the default
     * {@link HTTPBuilder#setContentType(Object) content-type} for this instance.
     * </p>
     *
     * @param args named parameters - see
     *  {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
     * @return a {@link HttpResponseDecorator}, unless the default success
     *      handler is overridden.
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    @Override public Object post( Map<String,?> args )
            throws URISyntaxException, ClientProtocolException, IOException {
        return doRequest( new RequestConfigDelegate( args, new HttpPost(), null ) );
    }

    /**
     * <p> Convenience method to perform a PUT request.</p>
     *
     * <p>The request body (specified by a <code>body</code> named parameter)
     * will be encoded based on the <code>requestContentType</code> named
     * parameter, or if none is given, the default
     * {@link HTTPBuilder#setContentType(Object) content-type} for this instance.
     * </p>
     *
     * @param args named parameters - see
     *  {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
     * @return a {@link HttpResponseDecorator}, unless the default success
     *      handler is overridden.
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public Object put( Map<String,?> args ) throws URISyntaxException,
            ClientProtocolException, IOException {
        return this.doRequest( new RequestConfigDelegate( args, new HttpPut(), null ) );
    }

   /**
     * <p> Convenience method to perform a PATCH request.</p>
     *
     * <p>The request body (specified by a <code>body</code> named parameter)
     * will be encoded based on the <code>requestContentType</code> named
     * parameter, or if none is given, the default
     * {@link HTTPBuilder#setContentType(Object) content-type} for this instance.
     * </p>
     *
     * @param args named parameters - see
     *  {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
     * @return a {@link HttpResponseDecorator}, unless the default success
     *      handler is overridden.
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public Object patch( Map<String,?> args ) throws URISyntaxException,
            ClientProtocolException, IOException {
        return this.doRequest( new RequestConfigDelegate( args, new HttpPatch(), null ) );
    }

    /**
     * <p>Perform a HEAD request, often used to check preconditions before
     * sending a large PUT or POST request.</p>
     *
     * @param args named parameters - see
     *  {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
     * @return a {@link HttpResponseDecorator}, unless the default success
     *      handler is overridden.
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public Object head( Map<String,?> args ) throws URISyntaxException,
            ClientProtocolException, IOException {
        return this.doRequest( new RequestConfigDelegate( args, new HttpHead(), null ) );
    }

    /**
     * <p>Perform a DELETE request.  This method does not accept a
     * <code>body</code> argument.</p>
     *
     * @param args named parameters - see
     *  {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
     * @return a {@link HttpResponseDecorator}, unless the default success
     *      handler is overridden.
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public Object delete( Map<String,?> args ) throws URISyntaxException,
            ClientProtocolException, IOException {
        return this.doRequest( new RequestConfigDelegate( args, new HttpDelete(), null ) );
    }

    /**
     * <p>Perform an OPTIONS request.</p>
     *
     * @param args named parameters - see
     *  {@link HTTPBuilder.RequestConfigDelegate#setPropertiesFromMap(Map)}
     * @return a {@link HttpResponseDecorator}, unless the default success
     *      handler is overridden.
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public Object options( Map<String,?> args ) throws ClientProtocolException,
            IOException, URISyntaxException {
        return this.doRequest( new RequestConfigDelegate( args, new HttpOptions(), null ) );
    }

    /**
     * Returns an {@link HttpResponseDecorator}, which provides simplified
     * access to headers, response code, and parsed response body, as well as
     * the underlying {@link HttpResponse} instance.
     */
    @Override
    protected HttpResponseDecorator defaultSuccessHandler( HttpResponseDecorator resp, Object data )
            throws ResponseParseException {
        resp.setData( super.defaultSuccessHandler( resp, data ) );
        return resp;
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
    protected void defaultFailureHandler( HttpResponseDecorator resp, Object data )
            throws HttpResponseException {
        resp.setData( super.defaultSuccessHandler( resp, data ) );
        throw new HttpResponseException( resp );
    }
}

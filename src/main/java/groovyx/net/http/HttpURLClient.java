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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
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

import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.HttpURLConnectionRequestAdapter;
import oauth.signpost.exception.OAuthException;

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
import org.codehaus.groovy.runtime.EncodingGroovyMethods;

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
 * TODO request encoding support (if anyone asks for it)
 *
 * @see <a href='http://code.google.com/appengine/docs/java/urlfetch/overview.html'>GAE URLFetch</a>
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 * @since 0.5.0
 */
public class HttpURLClient {

    private Map<String,String> defaultHeaders = new HashMap<String,String>();
    private EncoderRegistry encoderRegistry = new EncoderRegistry();
    private ParserRegistry parserRegistry = new ParserRegistry();
    private Object contentType = ContentType.ANY;
    private Object requestContentType = null;
    private URIBuilder defaultURL = null;
    private boolean followRedirects = true;
    protected OAuthWrapper oauth;

    /** Logger instance defined for use by sub-classes */
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

        // copy so we don't modify the original collection when removing items:
        args = new HashMap<String,Object>(args);

        Object arg = args.remove( "url" );
        if ( arg == null && this.defaultURL == null )
            throw new IllegalStateException( "Either the 'defaultURL' property" +
                    " must be set or a 'url' parameter must be passed to the " +
                    "request method." );
        URIBuilder url = arg != null ? new URIBuilder( arg.toString() ) : defaultURL.clone();

        arg = null;
        arg = args.remove( "path" );
        if ( arg != null ) url.setPath( arg.toString() );
        arg = null;
        arg = args.remove( "query" );
        if ( arg != null ) {
            if ( ! ( arg instanceof Map<?,?> ) )
                throw new IllegalArgumentException( "'query' must be a map" );
            url.setQuery( (Map<?,?>)arg );
        }

        HttpURLConnection conn = (HttpURLConnection)url.toURL().openConnection();
        conn.setInstanceFollowRedirects( this.followRedirects );

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
            if ( oauth != null ) log.warn( "You are trying to use both OAuth and basic authentication!" );
            try {
                List<?> vals = (List<?>)arg;
                conn.addRequestProperty( "Authorization", getBasicAuthHeader(
                        vals.get(0).toString(), vals.get(1).toString() ) );
            } catch ( Exception ex ) {
                throw new IllegalArgumentException(
                        "Auth argument must be a list in the form [user,pass]" );
            }
        }

        arg = null;
        arg = args.remove( "headers" );
        if ( arg != null ) {
            if ( ! ( arg instanceof Map<?,?> ) )
                throw new IllegalArgumentException( "'headers' must be a map" );
            Map<?,?> headers = (Map<?,?>)arg;
            for ( Object key : headers.keySet() ) conn.addRequestProperty(
                    key.toString(), headers.get( key ).toString() );
        }


        arg = null;
        arg = args.remove( "body" );
        if ( arg != null ) {  // if there is a request POST or PUT body
            conn.setDoOutput( true );
            final HttpEntity body = (HttpEntity)encoderRegistry.getAt(
                    requestContentType ).call( arg );
            // TODO configurable request charset

            //TODO don't override if there is a 'content-type' in the headers list
            conn.addRequestProperty( "Content-Type", requestContentType );
            try {
                // OAuth Sign if necessary.
                if ( oauth != null ) conn = oauth.sign( conn, body );
                // send request data
                DefaultGroovyMethods.leftShift( conn.getOutputStream(),
                        body.getContent() );
            }
            finally { conn.getOutputStream().close(); }
        }
        // sign the request if we're using OAuth
        else if ( oauth != null ) conn = oauth.sign(conn, null);

        if ( args.size() > 0 ) {
            String illegalArgs = "";
            for ( String k : args.keySet() ) illegalArgs += k + ",";
            throw new IllegalArgumentException("Unknown named parameters: " + illegalArgs);
        }

        String method = conn.getRequestMethod();
        log.debug( method + " " + url );

        HttpResponse response = new HttpURLResponseAdapter(conn);
        if ( ContentType.ANY.equals( contentType ) ) contentType = conn.getContentType();

        Object result = this.getparsedResult(method, contentType, response);

        log.debug( response.getStatusLine() );
        HttpResponseDecorator decoratedResponse = new HttpResponseDecorator( response, result );

        if ( log.isTraceEnabled() ) {
            for ( Header h : decoratedResponse.getHeaders() )
                log.trace( " << " + h.getName() + " : " + h.getValue() );
        }

        if ( conn.getResponseCode() > 399 )
            throw new HttpResponseException( decoratedResponse );

        return decoratedResponse;
    }

    private Object getparsedResult( String method, Object contentType, HttpResponse response )
            throws ResponseParseException {

        Object parsedData = method.equals( "HEAD" ) || method.equals( "OPTIONS" ) ?
                null : parserRegistry.getAt( contentType ).call( response );
        try {
            //If response is streaming, buffer it in a byte array:
            if ( parsedData instanceof InputStream ) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DefaultGroovyMethods.leftShift( buffer, (InputStream)parsedData );
                parsedData = new ByteArrayInputStream( buffer.toByteArray() );
            }
            else if ( parsedData instanceof Reader ) {
                StringWriter buffer = new StringWriter();
                DefaultGroovyMethods.leftShift( buffer, (Reader)parsedData );
                parsedData = new StringReader( buffer.toString() );
            }
            else if ( parsedData instanceof Closeable )
                log.warn( "Parsed data is streaming, but cannot be buffered: " + parsedData.getClass() );
            return parsedData;
        }
        catch ( IOException ex ) {
            throw new ResponseParseException( new HttpResponseDecorator(response,null), ex );
        }
    }

    private String getBasicAuthHeader( String user, String pass ) throws UnsupportedEncodingException {
      return "Basic " + EncodingGroovyMethods.encodeBase64(
              (user + ":" + pass).getBytes("ISO-8859-1") ).toString();
    }

    /**
     * Set basic user and password authorization to be used for every request.
     * Pass <code>null</code> to un-set authorization for this instance.
     * @param user
     * @param pass
     * @throws UnsupportedEncodingException
     */
    public void setBasicAuth( Object user, Object pass ) throws UnsupportedEncodingException {
        if ( user == null ) this.defaultHeaders.remove( "Authorization" );
        else this.defaultHeaders.put( "Authorization",
                getBasicAuthHeader( user.toString(), pass.toString() ) );
    }

    /**
     * Sign all outbound requests with the given OAuth keys and tokens.  It
     * is assumed you have already generated a consumer keypair and retrieved
     * a proper access token pair from your target service (see
     * <a href='http://code.google.com/p/oauth-signpost/wiki/TwitterAndSignpost'>Signpost documentation</a>
     * for more details.)  Once this has been done all requests will be signed.
     * @param consumerKey null if you want to _stop_ signing requests.
     * @param consumerSecret
     * @param accessToken
     * @param accessSecret
     */
    public void setOAuth( Object consumerKey, Object consumerSecret,
            Object accessToken, Object accessSecret ) {
        if ( consumerKey == null ) {
            oauth = null;
            return;
        }
        this.oauth = new OAuthWrapper(consumerKey, consumerSecret, accessToken, accessSecret);
    }

    /**
     * This class basically wraps Signpost classes so they are not loaded
     * until {@link HttpURLClient#setOAuth(Object, Object, Object, Object)}
     * is called.  This allows Signpost to act as an optional
     * dependency.  If you are not using Signpost, you don't need the JAR
     * on your classpath.
     * @since 0.5.1
     */
    private static class OAuthWrapper {
        protected OAuthConsumer oauth;
        OAuthWrapper( Object consumerKey, Object consumerSecret,
            Object accessToken, Object accessSecret ) {
            oauth = new DefaultOAuthConsumer( consumerKey.toString(), consumerSecret.toString() );
            oauth.setTokenWithSecret( accessToken.toString(), accessSecret.toString() );
        }

        HttpURLConnection sign( HttpURLConnection request, final HttpEntity body ) throws IOException {
            try {  // OAuth Sign.
                // Note that the request body must be repeatable even though it is an input stream.
                if ( body == null ) return (HttpURLConnection)oauth.sign( request ).unwrap();
                else return (HttpURLConnection)oauth.sign(
                        new HttpURLConnectionRequestAdapter(request) {
                            /* @Override */
                            public InputStream getMessagePayload() throws IOException {
                                return body.getContent();
                            }
                        }).unwrap();
            }
            catch ( final OAuthException ex ) {
//              throw new IOException( "OAuth signing error", ex ); // 1.6 only!
                throw new IOException( "OAuth signing error: " + ex.getMessage() ) {
                    private static final long serialVersionUID = -13848840190384656L;
                    /* @Override */ public Throwable getCause() { return ex; }
                };
            }
        }
    }

    /**
     * Control whether this instance should automatically follow redirect
     * responses. See {@link HttpURLConnection#setInstanceFollowRedirects(boolean)}
     * @param follow true if the connection should automatically follow
     * redirect responses from the server.
     */
    public void setFollowRedirects( boolean follow ) {
        this.followRedirects = follow;
    }

    /**
     * See {@link #setFollowRedirects(boolean)}
     * @return
     */
    public boolean isFollowRedirects() { return this.followRedirects; }

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

    /**
     * This class makes a HttpURLConnection look like an HttpResponse for use
     * by {@link ParserRegistry} and {@link HttpResponseDecorator}.
     */
    private final class HttpURLResponseAdapter implements HttpResponse {

        HttpURLConnection conn;
        Header[] headers;

        HttpURLResponseAdapter( HttpURLConnection conn ) {
            this.conn = conn;
        }

        public HttpEntity getEntity() {
            return new HttpEntity() {

                public void consumeContent() throws IOException {
                    conn.getInputStream().close();
                }

                public InputStream getContent()
                        throws IOException, IllegalStateException {
                    if ( Status.find( conn.getResponseCode() )
                            == Status.FAILURE ) return conn.getErrorStream();
                    return conn.getInputStream();
                }

                public Header getContentEncoding() {
                    return new BasicHeader( "Content-Encoding",
                            conn.getContentEncoding() );
                }

                public long getContentLength() {
                    return conn.getContentLength();
                }

                public Header getContentType() {
                    return new BasicHeader( "Content-Type", conn.getContentType() );
                }

                public boolean isChunked() {
                    String enc = conn.getHeaderField( "Transfer-Encoding" );
                    return enc != null && enc.contains( "chunked" );
                }

                public boolean isRepeatable() {
                    return false;
                }

                public boolean isStreaming() {
                    return true;
                }

                public void writeTo( OutputStream out ) throws IOException {
                    DefaultGroovyMethods.leftShift( out, conn.getInputStream() );
                }

            };
        }

        public Locale getLocale() {  //TODO test me
            String val = conn.getHeaderField( "Locale" );
            return val != null ? new Locale( val ) : Locale.getDefault();
        }

        public StatusLine getStatusLine() {
            try {
                return new BasicStatusLine( this.getProtocolVersion(),
                    conn.getResponseCode(), conn.getResponseMessage() );
            } catch ( IOException ex ) {
                throw new RuntimeException( "Error reading status line", ex );
            }
        }

        public boolean containsHeader( String key ) {
            return conn.getHeaderField( key ) != null;
        }

        public Header[] getAllHeaders() {
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

        public Header getFirstHeader( String key ) {
            for ( Header h : getAllHeaders() )
                if ( h.getName().equals( key ) ) return h;
            return null;
        }

        /**
         * Note that HttpURLConnection does not support multiple headers of
         * the same name.
         */
        public Header[] getHeaders( String key ) {
            List<Header> headers = new ArrayList<Header>();
            for ( Header h : getAllHeaders() )
                if ( h.getName().equals( key ) ) headers.add( h );
            return headers.toArray( new Header[headers.size()] );
        }

        /**
         * @see URLConnection#getHeaderField(String)
         */
        public Header getLastHeader( String key ) {
            String val = conn.getHeaderField( key );
            return val != null ? new BasicHeader( key, val ) : null;
        }

        public HttpParams getParams() { return null; }

        public ProtocolVersion getProtocolVersion() {
            /* TODO this could potentially cause problems if the server is
               using HTTP 1.0 */
            return new ProtocolVersion( "HTTP", 1, 1 );
        }

        public HeaderIterator headerIterator() {
            return new BasicHeaderIterator( this.getAllHeaders(), null );
        }

        public HeaderIterator headerIterator( String key ) {
            return new BasicHeaderIterator( this.getHeaders( key ), key );
        }

        /* Setters are part of the interface, but aren't applicable for this
         * adapter */
        public void setEntity( HttpEntity entity ) {}
        public void setLocale( Locale l ) {}
        public void setReasonPhrase( String phrase ) {}
        public void setStatusCode( int code ) {}
        public void setStatusLine( StatusLine line ) {}
        public void setStatusLine( ProtocolVersion v, int code ) {}
        public void setStatusLine( ProtocolVersion arg0,
                int arg1, String arg2 ) {}
        public void addHeader( Header arg0 ) {}
        public void addHeader( String arg0, String arg1 ) {}
        public void removeHeader( Header arg0 ) {}
        public void removeHeaders( String arg0 ) {}
        public void setHeader( Header arg0 ) {}
        public void setHeader( String arg0, String arg1 ) {}
        public void setHeaders( Header[] arg0 ) {}
        public void setParams( HttpParams arg0 ) {}
    }

    /**
     * Retrieve the default headers that will be sent in each request.  Note
     * that this is a 'live' map that can be directly manipulated to add or
     * remove the default request headers.
     * @return
     */
    public Map<String,String> getHeaders() {
        return defaultHeaders;
    }

    /**
     * Set default headers to be sent with every request.
     * @param headers
     */
    public void setHeaders( Map<?,?> headers ) {
        this.defaultHeaders.clear();
        for ( Object key : headers.keySet() ) {
            Object val = headers.get( key );
            if ( val != null ) this.defaultHeaders.put(
                    key.toString(), val.toString() );
        }
    }

    /**
     * Get the encoder registry used by this instance, which can be used
     * to directly modify the request serialization behavior.
     * i.e. <code>client.encoders.'application/xml' = {....}</code>.
     * @return
     */
    public EncoderRegistry getEncoders() {
        return encoderRegistry;
    }

    public void setEncoders( EncoderRegistry encoderRegistry ) {
        this.encoderRegistry = encoderRegistry;
    }

    /**
     * Retrieve the parser registry used by this instance, which can be used to
     * directly modify the parsing behavior.
     * @return
     */
    public ParserRegistry getParsers() {
        return parserRegistry;
    }

    public void setParsers( ParserRegistry parserRegistry ) {
        this.parserRegistry = parserRegistry;
    }

    /**
     * Get the default content-type used for parsing response data.
     * @return a String or {@link ContentType} object.  Defaults to
     * {@link ContentType#ANY}
     */
    public Object getContentType() {
        return contentType;
    }

    /**
     * Set the default content-type used to control response parsing and request
     * serialization behavior.  If <code>null</code> is passed,
     * {@link ContentType#ANY} will be used.  If this value is
     * {@link ContentType#ANY}, the response <code>Content-Type</code> header is
     * used to parse the response.
     * @param ct a String or {@link ContentType} value.
     */
    public void setContentType( Object ct ) {
        this.contentType = (ct == null) ? ContentType.ANY : ct;
    }

    /**
     * Get the default content-type used to serialize the request data.
     * @return
     */
    public Object getRequestContentType() {
        return requestContentType;
    }

    /**
     * Set the default content-type used to control request body serialization.
     * If null, the {@link #getContentType() contentType property} is used.
     * Additionally, if the <code>contentType</code> is {@link ContentType#ANY},
     * a <code>requestContentType</code> <i>must</i> be specified when
     * performing a POST or PUT request that sends request data.
     * @param requestContentType String or {@link ContentType} value.
     */
    public void setRequestContentType( Object requestContentType ) {
        this.requestContentType = requestContentType;
    }
}

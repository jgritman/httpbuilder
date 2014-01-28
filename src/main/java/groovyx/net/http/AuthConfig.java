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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthException;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

/**
 * Encapsulates all configuration related to HTTP authentication methods.
 * @see HTTPBuilder#getAuth()
 *
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public class AuthConfig {
    protected HTTPBuilder builder;
    public AuthConfig( HTTPBuilder builder ) {
        this.builder = builder;
    }

    /**
     * Set authentication credentials to be used for the current
     * {@link HTTPBuilder#getUri() default host}.  This method name is a bit of
     * a misnomer, since these credentials will actually work for "digest"
     * authentication as well.
     * @param user
     * @param pass
     */
    public void basic( String user, String pass ) {
        URI uri = ((URIBuilder)builder.getUri()).toURI();
        if ( uri == null ) throw new IllegalStateException( "a default URI must be set" );
        this.basic( uri.getHost(), uri.getPort(), user, pass );
    }

    /**
     * Set authentication credentials to be used for the given host and port.
     * @param host
     * @param port
     * @param user
     * @param pass
     */
    public void basic( String host, int port, String user, String pass ) {
	  final HttpClient client = builder.getClient();
	  if ( !(client instanceof AbstractHttpClient )) {
		throw new IllegalStateException("client is not an AbstractHttpClient");
	  }
      ((AbstractHttpClient)client).getCredentialsProvider().setCredentials(
            new AuthScope( host, port ),
            new UsernamePasswordCredentials( user, pass )
        );
    }

    /**
     * Set NTLM authentication credentials to be used for the current
     * {@link HTTPBuilder#getUri() default host}.
     * @param user
     * @param pass
     * @param workstation
     * @param domain
     */
    public void ntlm( String user, String pass, String workstation, String domain ) {
        URI uri = ((URIBuilder)builder.getUri()).toURI();
        if ( uri == null ) throw new IllegalStateException( "a default URI must be set" );
        this.ntlm( uri.getHost(), uri.getPort(), user, pass, workstation, domain );
    }

    /**
     * Set NTLM authentication credentials to be used for the given host and port.
     * @param host
     * @param port
     * @param user
     * @param pass
     * @param workstation
     * @param domain
     */
    public void ntlm( String host, int port, String user, String pass, String workstation, String domain ) {
      final HttpClient client = builder.getClient();
      if ( !(client instanceof AbstractHttpClient )) {
        throw new IllegalStateException("client is not an AbstractHttpClient");
      }
      ((AbstractHttpClient)client).getCredentialsProvider().setCredentials(
            new AuthScope( host, port ),
            new NTCredentials( user, pass, workstation, domain )
        );
    }

    /**
     * Sets a certificate to be used for SSL authentication.  See
     * {@link Class#getResource(String)} for how to get a URL from a resource
     * on the classpath.
     * @param certURL URL to a JKS keystore where the certificate is stored.
     * @param password password to decrypt the keystore
     */
    public void certificate( String certURL, String password )
            throws GeneralSecurityException, IOException {

        KeyStore keyStore = KeyStore.getInstance( KeyStore.getDefaultType() );
        InputStream jksStream = new URL(certURL).openStream();
        try {
            keyStore.load( jksStream, password.toCharArray() );
        } finally { jksStream.close(); }

        SSLSocketFactory ssl = new SSLSocketFactory(keyStore, password);
        ssl.setHostnameVerifier( SSLSocketFactory.STRICT_HOSTNAME_VERIFIER );

        builder.getClient().getConnectionManager().getSchemeRegistry()
            .register( new Scheme("https", ssl, 443) );
    }

    /**
     * </p>OAuth sign all requests.  Note that this currently does <strong>not</strong>
     * wait for a <code>WWW-Authenticate</code> challenge before sending the
     * the OAuth header.  All requests to all domains will be signed for this
     * instance.</p>
     *
     * <p>This assumes you've already generated an <code>accessToken</code> and
     * <code>secretToken</code> for the site you're targeting.  For More information
     * on how to achieve this, see the
     * <a href='http://code.google.com/p/oauth-signpost/wiki/GettingStarted#Using_Signpost'>Signpost documentation</a>.</p>
     * @since 0.5.1
     * @param consumerKey <code>null</code> if you want to <strong>unset</strong>
     *  OAuth handling and stop signing requests.
     * @param consumerSecret
     * @param accessToken
     * @param secretToken
     */
    public void oauth( String consumerKey, String consumerSecret,
            String accessToken, String secretToken ) {
        	  final HttpClient client = builder.getClient();
	    if ( !(client instanceof AbstractHttpClient )) {
		  throw new IllegalStateException("client is not an AbstractHttpClient");
	    }
        ((AbstractHttpClient)client).removeRequestInterceptorByClass( OAuthSigner.class );
        if ( consumerKey != null )
            ((AbstractHttpClient)client).addRequestInterceptor( new OAuthSigner(
                consumerKey, consumerSecret, accessToken, secretToken ) );
    }

    /**
     * This class is used to sign all requests via an {@link HttpRequestInterceptor}
     * until the context-aware AuthScheme is released in HttpClient 4.1.
     * @since 0.5.1
     */
    static class OAuthSigner implements HttpRequestInterceptor {
        protected OAuthConsumer oauth;
        public OAuthSigner( String consumerKey, String consumerSecret,
            String accessToken, String secretToken ) {
            this.oauth = new CommonsHttpOAuthConsumer( consumerKey, consumerSecret );
            oauth.setTokenWithSecret( accessToken, secretToken );
        }

        public void process(HttpRequest request, HttpContext ctx) throws HttpException, IOException {
            /* The full request URI must be reconstructed between the context and the request URI.
             * Best we can do until AuthScheme supports HttpContext.  See:
             * https://issues.apache.org/jira/browse/HTTPCLIENT-901 */
            try {
                HttpHost host = (HttpHost) ctx.getAttribute( ExecutionContext.HTTP_TARGET_HOST );
                final URI requestURI = new URI( host.toURI() ).resolve( request.getRequestLine().getUri() );

                oauth.signpost.http.HttpRequest oAuthRequest =
                    new OAuthRequestAdapter(request, requestURI);
                this.oauth.sign( oAuthRequest );
            }
            catch ( URISyntaxException ex ) {
                throw new HttpException( "Error rebuilding request URI", ex );
            }
            catch (OAuthException e) {
                throw new HttpException( "OAuth signing error", e);
            }
        }

        static class OAuthRequestAdapter implements oauth.signpost.http.HttpRequest {

            final HttpRequest request;
            final URI requestURI;
            OAuthRequestAdapter( HttpRequest request, URI requestURI ) {
                this.request = request;
                this.requestURI = requestURI;
            }

            public String getRequestUrl() { return requestURI.toString(); }
            public void setRequestUrl(String url) {/*ignore*/}
            public Map<String, String> getAllHeaders() {
                Map<String,String> headers = new HashMap<String,String>();
                // FIXME this doesn't account for repeated headers,
                // which are allowed by the HTTP spec!!
                for ( Header h : request.getAllHeaders() )
                    headers.put(h.getName(), h.getValue());
                return headers;
            }
            public String getContentType() {
                try {
                    return request.getFirstHeader("content-type").getValue();
                }
                catch ( Exception ex ) { // NPE or ArrayOOBEx
                    return null;
                }
            }
            public String getHeader(String name) {
                Header h = request.getFirstHeader(name);
                return h != null ? h.getValue() : null;
            }
            public InputStream getMessagePayload() throws IOException {
                if ( request instanceof HttpEntityEnclosingRequest )
                    return ((HttpEntityEnclosingRequest)request).getEntity().getContent();
                return null;
            }
            public String getMethod() {
                return request.getRequestLine().getMethod();
            }
            public void setHeader(String key, String val) {
                request.setHeader(key, val);
            }
            public Object unwrap() {
                return request;
            }
        };
    }
}

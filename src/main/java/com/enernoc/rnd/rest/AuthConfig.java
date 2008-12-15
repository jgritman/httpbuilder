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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;

/**
 * Encapsulates all configuration related to HTTP authentication methods.
 * @see HTTPBuilder#getAuth()
 * 
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 */
public class AuthConfig {
	protected HTTPBuilder builder;
	public AuthConfig( HTTPBuilder builder ) {
		this.builder = builder;
	}
	
	/**
	 * Set authentication credentials to be used for the current 
	 * {@link HTTPBuilder#getURL() default host}.  
	 * @param user
	 * @param pass
	 */
	public void basic( String user, String pass ) {
		URL url = (URL)builder.getURL();
		this.basic( url.getHost(), url.getPort(),
				user, pass );
	}
	
	/**
	 * Set authentication credentials to be used for the given host and port. 
	 * @param host
	 * @param port
	 * @param user
	 * @param pass
	 */
	public void basic( String host, int port, String user, String pass ) {
		builder.getClient().getCredentialsProvider().setCredentials( 
			new AuthScope( host, port ),
			new UsernamePasswordCredentials( user, pass )
		);
	}
	
	/**
	 * Sets a certificate to be used for SSL authentication.  
	 * @param certURL URL to a JKS keystore where the certificate is stored
	 * @param password password to decrypt the keystore
	 */
	public void certificate( String certURL, String password ) 
			throws GeneralSecurityException, MalformedURLException, IOException {
		
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
}
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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

/**
 * This class implements a mutable URI.  All <code>set</code>, <code>add</code> 
 * and <code>remove</code> methods affect this class' internal URI 
 * representation.  All mutator methods support chaining, e.g.
 * <pre>
 * new URIBuilder("http://www.google.com/")
 *   .setScheme( "https" )
 *   .setPort( 443 )
 *   .setPath( "some/path" )
 *   .toString();
 * </pre>
 * A slightly more 'Groovy' version would be:
 * <pre>
 * new URIBuilder('http://www.google.com/').with {
 *    scheme = 'https'
 *    port = 443
 *    path = 'some/path'
 *    query = [p1:1, p2:'two']
 * }.toString()
 * </pre>
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 */
public class URIBuilder {
	protected URI base;
	private final String ENC = "UTF-8"; 
	
	public URIBuilder( String url ) throws URISyntaxException {
		base = new URI(url);
	}
	
	public URIBuilder( URL url ) throws URISyntaxException {
		this.base = url.toURI();
	}
	
	public URIBuilder( URI url ) {
		this.base = url;
	}
	
	/**
	 * Attempts to convert a URL or String to a URI.
	 * @param uri a {@link URI}, {@link URL} or any object that produces a 
	 *   parse-able URI string from its <code>toString()</code> result.
	 * @return a valid URI parsed from the given object
	 * @throws URISyntaxException
	 */
	public static URI convertToURI( Object uri ) throws URISyntaxException {
		if ( uri instanceof URI ) ;
		else if ( uri instanceof URL ) uri = ((URL)uri).toURI();
		else uri = new URI( uri.toString() ); // assume any other object type produces a valid URI string
		return (URI)uri;
	}
	
	/**
	 * AKA protocol 
	 * @throws URISyntaxException 
	 */
	public URIBuilder setScheme( String scheme ) throws URISyntaxException {
		this.base = new URI( scheme, base.getUserInfo(), 
				base.getHost(), base.getPort(), base.getPath(),
				base.getQuery(), base.getFragment() );
		return this;
	}
	
	public URIBuilder setPort( int port ) throws URISyntaxException {
		this.base = new URI( base.getScheme(), base.getUserInfo(), 
				base.getHost(), port, base.getPath(),
				base.getQuery(), base.getFragment() );
		return this;
	}
	
	public URIBuilder setHost( String host ) throws URISyntaxException {
		this.base = new URI( base.getScheme(), base.getUserInfo(), 
				host, base.getPort(), base.getPath(),
				base.getQuery(), base.getFragment() );
		return this;
	}
	
	public URIBuilder setPath( String path ) throws URISyntaxException {
		path = base.resolve( path ).getPath();
		this.base = new URI( base.getScheme(), base.getUserInfo(), 
				base.getHost(), base.getPort(), path,
				base.getQuery(), base.getFragment() );
		return this;
	}
	
	/**
	 * Set the query portion of the URI
	 * @param params a Map of parameters that will be transformed into the query string
	 * @return
	 * @throws URISyntaxException
	 */
	public URIBuilder setQuery( Map<String,?> params ) throws URISyntaxException {
		List<NameValuePair> pairs = new ArrayList<NameValuePair>(params.size());
		for ( Map.Entry<String, ?> entry : params.entrySet() ) {
			String val = ( entry.getValue() != null ) ? 
					entry.getValue().toString() : ""; 
			pairs.add( new BasicNameValuePair( 
					entry.getKey(), val ) );
		}
		String queryString = URLEncodedUtils.format( pairs, ENC );
		this.base = new URI( base.getScheme(), base.getUserInfo(), 
				base.getHost(), base.getPort(), base.getPath(),
				queryString, base.getFragment() );
		return this;
	}
	
	/**
	 * Get the query string as a map
	 * @return
	 */
	public Map<String,String> getQuery() {
		Map<String,String> params = new HashMap<String, String>();		
		List<NameValuePair> pairs = URLEncodedUtils.parse( this.base, ENC );
		for ( NameValuePair pair : pairs ) 
			params.put( pair.getName(), pair.getValue() );
		return params;
	}
	
	public boolean hasQueryParam( String name ) {
		return getQuery().get( name ) != null;
	}
	
	public URIBuilder removeQueryParam( String param ) throws URISyntaxException {
		Map<String,String> params = getQuery();
		params.remove( param );
		this.setQuery( params );
		return this;
	}
	
	/**
	 * This will append a param to the existing query string.  If the given 
	 * param is already part of the query string, it will be replaced.
	 * @param param
	 * @param value
	 * @throws URISyntaxException 
	 */
	public URIBuilder addQueryParam( String param, Object value ) throws URISyntaxException {
		Map<String,String> params = getQuery();
		if ( value == null ) value = ""; 
		params.put( param, value.toString() );
		this.setQuery( params );
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public URIBuilder addQueryParams( Map<String,?> params ) throws URISyntaxException {
		Map existing = this.getQuery();
		existing.putAll( params );
		this.setQuery( existing );
		return this;
	}
	
	/**
	 * The document fragment, without a preceeding '#'
	 * @param fragment
	 * @throws URISyntaxException
	 */
	public URIBuilder setFragment( String fragment ) throws URISyntaxException {
		this.base = new URI( base.getScheme(), base.getUserInfo(), 
				base.getHost(), base.getPort(), base.getPath(),
				base.getQuery(), fragment );
		return this;
	}
	
	@Override public String toString() {
		return base.toString();
	}
	
	public URL toURL() throws MalformedURLException {
		return base.toURL();
	}
	
	public URI toURI() { return this.base; }
}

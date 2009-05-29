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
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public class URIBuilder implements Cloneable {
	protected URI base;
	private final String ENC = "UTF-8"; 
	
	public URIBuilder( String url ) throws URISyntaxException {
		base = new URI(url);
	}
	
	public URIBuilder( URL url ) throws URISyntaxException {
		this.base = url.toURI();
	}
	
	/**
	 * @throws IllegalArgumentException if uri is null
	 * @param uri
	 */
	public URIBuilder( URI uri ) throws IllegalArgumentException {
		if ( uri == null ) 
			throw new IllegalArgumentException( "uri cannot be null" );
		this.base = uri;
	}
	
	/**
	 * Utility method to convert a number of type to a URI instance. 
	 * @param uri a {@link URI}, {@link URL} or any object that produces a 
	 *   valid URI string from its <code>toString()</code> result.
	 * @return a valid URI parsed from the given object
	 * @throws URISyntaxException
	 */
	public static URI convertToURI( Object uri ) throws URISyntaxException {
		if ( uri instanceof URI ) return (URI)uri;
		if ( uri instanceof URL ) return ((URL)uri).toURI();
		if ( uri instanceof URIBuilder ) return ((URIBuilder)uri).toURI();
		return new URI( uri.toString() ); // assume any other object type produces a valid URI string
	}
	
	
	/**
	 * Set the URI scheme, AKA the 'protocol.'  e.g. 
	 * <code>setScheme('https')</code> 
	 * @throws URISyntaxException if the given scheme contains illegal characters. 
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
	
	/**
	 * Set the path component of this URI.  The value may be absolute or 
	 * relative to the current path.
	 * e.g. <pre>
	 *   def uri = new URIBuilder( 'http://localhost/p1/p2?a=1' )
	 *   
	 *   uri.path = '/p3/p2'
	 *   assert uri.toString() == 'http://localhost/p3/p2?a=1'
	 *   
	 *   uri.path = 'p2a'
	 *   assert uri.toString() == 'http://localhost/p3/p2a?a=1'
	 *   
	 *   uri.path = '../p4'
	 *   assert uri.toString() == 'http://localhost/p4?a=1&b=2&c=3#frag'
	 * <pre>
	 * @param path the path portion of this URI, relative to the current URI.
	 * @return this URIBuilder instance, for method chaining.
	 * @throws URISyntaxException if the given path contains characters that 
	 *   cannot be converted to a valid URI
	 */
	public URIBuilder setPath( String path ) throws URISyntaxException {
		this.base = base.resolve( new URI(null,null, path, base.getQuery(), base.getFragment()) );
//		path = base.resolve( path ).getPath();
//		this.base = new URI( base.getScheme(), base.getUserInfo(), 
//				base.getHost(), base.getPort(), path,
//				base.getQuery(), base.getFragment() );
		return this;
	}
	
	/**
	 * Set the query portion of the URI
	 * @param params a Map of parameters that will be transformed into the query string
	 * @return this URIBuilder instance, for method chaining.
	 * @throws URISyntaxException
	 */
	public URIBuilder setQuery( Map<?,?> params ) throws URISyntaxException {
		if ( params == null || params.size() < 1 ) {
			this.base = new URI( base.getScheme(), base.getUserInfo(), 
				base.getHost(), base.getPort(), base.getPath(),
				null, base.getFragment() );
		}
		else {
			/* Passing the query string in the URI constructor will 
			 * double-escape query parameters and goober things up.  So we have 
			 * to create a full path+query+fragment and use URI#resolve() to 
			 * create the new URI.  */
			List<NameValuePair> pairs = new ArrayList<NameValuePair>(params.size());
			StringBuilder sb = new StringBuilder();
			String path = base.getPath();
			if ( path != null ) sb.append( path );
			sb.append( '?' );
			for ( Object key : params.keySet() ) {
				Object val = params.get(key);
				pairs.add( new BasicNameValuePair( key.toString(), 
						( val != null ) ? val.toString() : "" ) );
			}
			sb.append( URLEncodedUtils.format( pairs, ENC ) ); 
			String frag = base.getFragment();
			if ( frag != null ) sb.append( '#' ).append( frag );
			this.base = base.resolve( sb.toString() );
		}
		return this;
	}
	
	/**
	 * Get the query string as a map.
	 * @return a map of String name/value pairs representing the URI's query 
	 * string.
	 */
	public Map<String,String> getQuery() {
		Map<String,String> params = new HashMap<String, String>();		
		List<NameValuePair> pairs = URLEncodedUtils.parse( this.base, ENC );
		for ( NameValuePair pair : pairs ) 
			params.put( pair.getName(), pair.getValue() );
		return params;
	}
	
	/**
	 * Indicates if the given parameter is already part of this URI's query 
	 * string.
	 * @param name the query parameter name
	 * @return true if the given parameter name is found in the query string of 
	 *    the URI.
	 */
	public boolean hasQueryParam( String name ) {
		return getQuery().get( name ) != null;
	}
	
	/**
	 * Remove the given query parameter from this URI's query string.
	 * @param param the query name to remove 
	 * @return this URIBuilder instance, for method chaining.
	 * @throws URISyntaxException
	 */
	public URIBuilder removeQueryParam( String param ) throws URISyntaxException {
		Map<String,String> params = getQuery();
		params.remove( param );
		this.setQuery( params );
		return this;
	}
	
	/**
	 * This will append a param to the existing query string.  If the given 
	 * param is already part of the query string, it will be replaced.
	 * @param param query parameter name 
	 * @param value query parameter value (will be converted to a string if 
	 *   not null.  If <code>value</code> is null, it will be set as the empty 
	 *   string.
	 * @return this URIBuilder instance, for method chaining.
	 * @throws URISyntaxException if the query parameter values cannot be 
	 * converted to a valid URI.
	 * @see #setQuery(Map) 
	 */
	public URIBuilder addQueryParam( String param, Object value ) throws URISyntaxException {
		Map<String,String> params = getQuery();
		if ( value == null ) value = ""; 
		params.put( param, value.toString() );
		this.setQuery( params );
		return this;
	}
	
	/**
	 * Add these parameters to the existing URIBuilder's parameter set.
	 * Parameters may be passed either as a single map argument, or as a list
	 * of named arguments.  e.g. 
	 * <pre>uriBuilder.addQueryParams( [one:1,two:2] )
	 * uriBuilder.addQueryParams( three : 3 )
	 * </pre>
	 * @param params parameters to add.
	 * @return this URIBuilder instance, for method chaining.
	 * @throws URISyntaxException
	 */
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
	 * @return this URIBuilder instance, for method chaining.
	 * @throws URISyntaxException if the given value contains illegal characters. 
	 */
	public URIBuilder setFragment( String fragment ) throws URISyntaxException {
		this.base = new URI( base.getScheme(), base.getUserInfo(), 
				base.getHost(), base.getPort(), base.getPath(),
				base.getQuery(), fragment );
		return this;
	}
	
	/**
	 * Print this builder's URI representation.
	 */
	@Override public String toString() {
		return base.toString();
	}
	
	/**
	 * Convenience method to convert this object to a URL instance.
	 * @return this builder as a URL
	 * @throws MalformedURLException if the underlying URI does not represent a 
	 * valid URL.
	 */
	public URL toURL() throws MalformedURLException {
		return base.toURL();
	}
	
	/**
	 * Convenience method to convert this object to a URI instance.
	 * @return this builder's underlying URI representation
	 */
	public URI toURI() { return this.base; }
	
	/**
	 * Implementation of Groovy's <code>as</code> operator, to allow type 
	 * conversion.  
	 * @param type <code>URL</code>, <code>URL</code>, or <code>String</code>.
	 * @return a representation of this URIBuilder instance in the given type
	 * @throws MalformedURLException if <code>type</code> is URL and this 
	 * URIBuilder instance does not represent a valid URL. 
	 */
	public Object asType( Class<?> type ) throws MalformedURLException {
		if ( type == URI.class ) return this.toURI();
		if ( type == URL.class ) return this.toURL();
		if ( type == String.class ) return this.toString();
		throw new ClassCastException( "Cannot cast instance of URIBuilder to class " + type );
	}
	
	/**
	 * Create a copy of this URIBuilder instance.
	 */
	@Override
	protected URIBuilder clone() {
		return new URIBuilder( this.base );
	}
	
	/**
	 * Determine if this URIBuilder is equal to another URIBuilder instance.
	 * @see URI#equals(Object)
	 * @return if <code>obj</code> is a URIBuilder instance whose underlying 
	 *   URI implementation is equal to this one's.
	 */
	@Override
	public boolean equals( Object obj ) {
		if ( ! ( obj instanceof URIBuilder) ) return false;
		return this.base.equals( ((URIBuilder)obj).toURI() );
	}
}

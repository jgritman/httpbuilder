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

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *    return it
 * }.toString()
 * </pre>
 *
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public class URIBuilder implements Cloneable {
    protected URI base;
    private final String ENC = "UTF-8";

    public URIBuilder(String url) throws URISyntaxException {
        base = new URI(url);
    }

    public URIBuilder(URL url) throws URISyntaxException {
        this.base = url.toURI();
    }

    /**
     * @param uri
     * @throws IllegalArgumentException if uri is null
     */
    public URIBuilder(URI uri) throws IllegalArgumentException {
        if (uri == null)
            throw new IllegalArgumentException("uri cannot be null");
        this.base = uri;
    }

    /**
     * Utility method to convert a number of type to a URI instance.
     *
     * @param uri a {@link URI}, {@link URL} or any object that produces a
     *            valid URI string from its <code>toString()</code> result.
     * @return a valid URI parsed from the given object
     * @throws URISyntaxException
     */
    public static URI convertToURI(Object uri) throws URISyntaxException {
        if (uri instanceof URI) return (URI) uri;
        if (uri instanceof URL) return ((URL) uri).toURI();
        if (uri instanceof URIBuilder) return ((URIBuilder) uri).toURI();
        return new URI(uri.toString()); // assume any other object type produces a valid URI string
    }

    protected URI update(String scheme, String userInfo, String host, int port,
                         String path, String query, String fragment) throws URISyntaxException {
        URI u = new URI(scheme, userInfo, host, port, base.getPath(), null, null);

        StringBuilder sb = new StringBuilder();
        if (path != null) sb.append(path);
        if (query != null)
            sb.append('?').append(query);
        if (fragment != null) sb.append('#').append(fragment);
        return u.resolve(sb.toString());
    }

    /**
     * Set the URI scheme, AKA the 'protocol.'  e.g.
     * <code>setScheme('https')</code>
     *
     * @throws URISyntaxException if the given scheme contains illegal characters.
     */
    public URIBuilder setScheme(String scheme) throws URISyntaxException {
        this.base = update(scheme, base.getUserInfo(),
                base.getHost(), base.getPort(),
                base.getRawPath(), base.getRawQuery(), base.getRawFragment());
        return this;
    }

    /**
     * Get the scheme for this URI.  See {@link URI#getScheme()}
     *
     * @return the scheme portion of the URI
     */
    public String getScheme() {
        return this.base.getScheme();
    }

    /**
     * Set the port for this URI, or <code>-1</code> to unset the port.
     *
     * @param port
     * @return this URIBuilder instance
     * @throws URISyntaxException
     */
    public URIBuilder setPort(int port) throws URISyntaxException {
        this.base = update(base.getScheme(), base.getUserInfo(),
                base.getHost(), port, base.getRawPath(),
                base.getRawQuery(), base.getRawFragment());
        return this;
    }

    /**
     * See {@link URI#getPort()}
     *
     * @return the port portion of this URI (-1 if a port is not specified.)
     */
    public int getPort() {
        return this.base.getPort();
    }

    /**
     * Set the host portion of this URI.
     *
     * @param host
     * @return this URIBuilder instance
     * @throws URISyntaxException if the host parameter contains illegal characters.
     */
    public URIBuilder setHost(String host) throws URISyntaxException {
        this.base = update(base.getScheme(), base.getUserInfo(),
                host, base.getPort(), base.getRawPath(),
                base.getRawQuery(), base.getRawFragment());
        return this;
    }

    /**
     * See {@link URI#getHost()}
     *
     * @return the host portion of the URI
     */
    public String getHost() {
        return base.getHost();
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
     *   assert uri.toString() == 'http://localhost/p4?a=1&amp;b=2&amp;c=3#frag';
     * </pre>
     *
     * @param path the path portion of this URI, relative to the current URI.
     * @return this URIBuilder instance, for method chaining.
     * @throws URISyntaxException if the given path contains characters that
     *                            cannot be converted to a valid URI
     */
    public URIBuilder setPath(String path) throws URISyntaxException {
        this.base = update(base.getScheme(), base.getUserInfo(),
                base.getHost(), base.getPort(),
                new URI(null, null, path, null, null).getRawPath(),
                base.getRawQuery(), base.getRawFragment());
        return this;
    }

    /**
     * Note that this property is <strong>not</strong> necessarily reflexive
     * with the {@link #setPath(String)} method!  <code>URIBuilder.setPath()</code>
     * will resolve a relative path, whereas this method will always return the
     * full, absolute path.
     * See {@link URI#getPath()}
     *
     * @return the full path portion of the URI.
     */
    public String getPath() {
        return this.base.getPath();
    }

    /* TODO null/ zero-size check if this is ever made public */
    protected URIBuilder setQueryNVP(List<NameValuePair> nvp) throws URISyntaxException {
        /* Passing the query string in the URI constructor will
         * double-escape query parameters and goober things up.  So we have
         * to create a full path+query+fragment and use URI#resolve() to
         * create the new URI.  */
        StringBuilder sb = new StringBuilder();
        String path = base.getRawPath();
        if (path != null) sb.append(path);
        sb.append('?');
        sb.append(URLEncodedUtils.format(nvp, ENC));
        String frag = base.getRawFragment();
        if (frag != null) sb.append('#').append(frag);
        this.base = base.resolve(sb.toString());

        return this;
    }

    /**
     * Set the query portion of the URI.  For query parameters with multiple
     * values, put the values in a list like so:
     * <pre>uri.query = [ p1:'val1', p2:['val2', 'val3'] ]
     * // will produce a query string of ?p1=val1&amp;p2=val2&amp;p2=val3</pre>
     *
     * @param params a Map of parameters that will be transformed into the query string
     * @return this URIBuilder instance, for method chaining.
     * @throws URISyntaxException
     */
    public URIBuilder setQuery(Map<?, ?> params) throws URISyntaxException {
        if (params == null || params.size() < 1) {
            this.base = new URI(base.getScheme(), base.getUserInfo(),
                    base.getHost(), base.getPort(), base.getPath(),
                    null, base.getFragment());
        } else {
            List<NameValuePair> nvp = new ArrayList<NameValuePair>(params.size());
            for (Object key : params.keySet()) {
                Object value = params.get(key);
                if (value instanceof List<?>) {
                    for (Object val : (List<?>) value)
                        nvp.add(new BasicNameValuePair(key.toString(),
                                (val != null) ? val.toString() : ""));
                } else nvp.add(new BasicNameValuePair(key.toString(),
                        (value != null) ? value.toString() : ""));
            }
            this.setQueryNVP(nvp);
        }
        return this;
    }

    /**
     * Set the raw, already-escaped query string.  No additional escaping will
     * be done on the string.
     *
     * @param query
     * @return
     */
    public URIBuilder setRawQuery(String query) throws URISyntaxException {
        this.base = update(base.getScheme(), base.getUserInfo(),
                base.getHost(), base.getPort(),
                base.getRawPath(), query, base.getRawFragment());
        return this;
    }

    /**
     * Get the query string as a map for convenience.  If any parameter contains
     * multiple values (e.g. <code>p1=one&amp;p1=two</code>) both values will be
     * inserted into a list for that paramter key (<code>[p1 : ['one','two']]
     * </code>).  Note that this is not a "live" map.  Therefore, you cannot
     * call
     * <pre> uri.query.a = 'BCD'</pre>
     * You will not modify the query string but instead the generated map of
     * parameters.  Instead, you need to use {@link #removeQueryParam(String)}
     * first, then {@link #addQueryParam(String, Object)}, or call
     * {@link #setQuery(Map)} which will set the entire query string.
     *
     * @return a map of String name/value pairs representing the URI's query
     * string.
     */
    public Map<String, Object> getQuery() {
        Map<String, Object> params = new HashMap<String, Object>();
        List<NameValuePair> pairs = this.getQueryNVP();
        if (pairs == null) return null;

        for (NameValuePair pair : pairs) {

            String key = pair.getName();
            Object existing = params.get(key);

            if (existing == null) params.put(key, pair.getValue());

            else if (existing instanceof List<?>)
                ((List) existing).add(pair.getValue());

            else {
                List<String> vals = new ArrayList<String>(2);
                vals.add((String) existing);
                vals.add(pair.getValue());
                params.put(key, vals);
            }
        }

        return params;
    }

    protected List<NameValuePair> getQueryNVP() {
        if (this.base.getQuery() == null) return null;
        List<NameValuePair> nvps = URLEncodedUtils.parse(this.base, ENC);
        List<NameValuePair> newList = new ArrayList<NameValuePair>();
        if (nvps != null) newList.addAll(nvps);
        return newList;
    }

    /**
     * Indicates if the given parameter is already part of this URI's query
     * string.
     *
     * @param name the query parameter name
     * @return true if the given parameter name is found in the query string of
     * the URI.
     */
    public boolean hasQueryParam(String name) {
        return getQuery().get(name) != null;
    }

    /**
     * Remove the given query parameter from this URI's query string.
     *
     * @param param the query name to remove
     * @return this URIBuilder instance, for method chaining.
     * @throws URISyntaxException
     */
    public URIBuilder removeQueryParam(String param) throws URISyntaxException {
        List<NameValuePair> params = getQueryNVP();
        NameValuePair found = null;
        for (NameValuePair nvp : params)  // BOO linear search.  Assume the list is small.
            if (nvp.getName().equals(param)) {
                found = nvp;
                break;
            }

        if (found == null) throw new IllegalArgumentException("Param '" + param + "' not found");
        params.remove(found);
        this.setQueryNVP(params);
        return this;
    }

    protected URIBuilder addQueryParam(NameValuePair nvp) throws URISyntaxException {
        List<NameValuePair> params = getQueryNVP();
        if (params == null) params = new ArrayList<NameValuePair>();
        params.add(nvp);
        this.setQueryNVP(params);
        return this;
    }

    /**
     * This will append a query parameter to the existing query string.  If the given
     * parameter is already part of the query string, it will be appended to.
     * To replace the existing value of a certain parameter, either call
     * {@link #removeQueryParam(String)} first, or use {@link #getQuery()},
     * modify the value in the map, then call {@link #setQuery(Map)}.
     *
     * @param param query parameter name
     * @param value query parameter value (will be converted to a string if
     *              not null.  If <code>value</code> is null, it will be set as the empty
     *              string.
     * @return this URIBuilder instance, for method chaining.
     * @throws URISyntaxException if the query parameter values cannot be
     *                            converted to a valid URI.
     * @see #setQuery(Map)
     */
    public URIBuilder addQueryParam(String param, Object value) throws URISyntaxException {
        this.addQueryParam(new BasicNameValuePair(param,
                (value != null) ? value.toString() : ""));
        return this;
    }

    protected URIBuilder addQueryParams(List<NameValuePair> nvp) throws URISyntaxException {
        List<NameValuePair> params = getQueryNVP();
        if (params == null) params = new ArrayList<NameValuePair>();
        params.addAll(nvp);
        this.setQueryNVP(params);
        return this;
    }

    /**
     * Add these parameters to the URIBuilder's existing query string.
     * Parameters may be passed either as a single map argument, or as a list
     * of named arguments.  e.g.
     * <pre> uriBuilder.addQueryParams( [one:1,two:2] )
     * uriBuilder.addQueryParams( three : 3 ) </pre>
     *
     * If any of the parameters already exist in the URI query, these values
     * will <strong>not</strong> replace them.  Multiple values for the same
     * query parameter may be added by putting them in a list. See
     * {@link #setQuery(Map)}.
     *
     * @param params parameters to add to the existing URI query (if any).
     * @return this URIBuilder instance, for method chaining.
     * @throws URISyntaxException
     */
    @SuppressWarnings("unchecked")
    public URIBuilder addQueryParams(Map<?, ?> params) throws URISyntaxException {
        List<NameValuePair> nvp = new ArrayList<NameValuePair>();
        for (Object key : params.keySet()) {
            Object value = params.get(key);
            if (value instanceof List) {
                for (Object val : (List) value)
                    nvp.add(new BasicNameValuePair(key.toString(),
                            (val != null) ? val.toString() : ""));
            } else nvp.add(new BasicNameValuePair(key.toString(),
                    (value != null) ? value.toString() : ""));
        }
        this.addQueryParams(nvp);
        return this;
    }

    /**
     * The document fragment, without a preceeding '#'.  Use <code>null</code>
     * to use no document fragment.
     *
     * @param fragment
     * @return this URIBuilder instance, for method chaining.
     * @throws URISyntaxException if the given value contains illegal characters.
     */
    public URIBuilder setFragment(String fragment) throws URISyntaxException {
        this.base = update(base.getScheme(), base.getUserInfo(),
                base.getHost(), base.getPort(), base.getRawPath(),
                base.getRawQuery(), new URI(null, null, null, fragment).getRawFragment());
        return this;
    }

    /**
     * See {@link URI#getFragment()}
     *
     * @return the URI document fragment
     */
    public String getFragment() {
        return this.base.getFragment();
    }

    /**
     * Set the userInfo portion of the URI, or <code>null</code> if the URI
     * should have no user information.
     *
     * @param userInfo
     * @return this URIBuilder instance
     * @throws URISyntaxException if the given value contains illegal characters.
     */
    public URIBuilder setUserInfo(String userInfo) throws URISyntaxException {
        this.base = update(base.getScheme(), userInfo,
                base.getHost(), base.getPort(), base.getRawPath(),
                base.getRawQuery(), base.getRawFragment());

        return this;
    }

    /**
     * See {@link URI#getUserInfo()}
     *
     * @return the user info portion of the URI, or <code>null</code> if it
     * is not specified.
     */
    public String getUserInfo() {
        return this.base.getUserInfo();
    }

    /**
     * Print this builder's URI representation.
     */
    @Override
    public String toString() {
        return base.toString();
    }

    /**
     * Convenience method to convert this object to a URL instance.
     *
     * @return this builder as a URL
     * @throws MalformedURLException if the underlying URI does not represent a
     *                               valid URL.
     */
    public URL toURL() throws MalformedURLException {
        return base.toURL();
    }

    /**
     * Convenience method to convert this object to a URI instance.
     *
     * @return this builder's underlying URI representation
     */
    public URI toURI() {
        return this.base;
    }

    /**
     * Implementation of Groovy's <code>as</code> operator, to allow type
     * conversion.
     *
     * @param type <code>URL</code>, <code>URL</code>, or <code>String</code>.
     * @return a representation of this URIBuilder instance in the given type
     * @throws MalformedURLException if <code>type</code> is URL and this
     *                               URIBuilder instance does not represent a valid URL.
     */
    public Object asType(Class<?> type) throws MalformedURLException {
        if (type == URI.class) return this.toURI();
        if (type == URL.class) return this.toURL();
        if (type == String.class) return this.toString();
        throw new ClassCastException("Cannot cast instance of URIBuilder to class " + type);
    }

    /**
     * Create a copy of this URIBuilder instance.
     */
    @Override
    protected URIBuilder clone() {
        return new URIBuilder(this.base);
    }

    /**
     * Determine if this URIBuilder is equal to another URIBuilder instance.
     *
     * @return if <code>obj</code> is a URIBuilder instance whose underlying
     * URI implementation is equal to this one's.
     * @see URI#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof URIBuilder)) return false;
        return this.base.equals(((URIBuilder) obj).toURI());
    }
}

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

import groovy.json.JsonSlurper;
import groovy.lang.Closure;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import groovyx.net.http.HTTPBuilder.RequestConfigDelegate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.codehaus.groovy.runtime.MethodClosure;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * <p>Keeps track of response parsers for each content type.  Each parser
 * should should be a closure that accepts an {@link HttpResponse} instance,
 * and returns whatever handler is appropriate for reading the response
 * data for that content-type.  For example, a plain-text response should
 * probably be parsed with a <code>Reader</code>, while an XML response
 * might be parsed by an XmlSlurper, which would then be passed to the
 * response closure. </p>
 *
 * <p>Note that all methods in this class assume {@link HttpResponse#getEntity()}
 * return a non-null value.  It is the job of the HTTPBuilder instance to ensure
 * a NullPointerException is not thrown by passing a response that contains no
 * entity.</p>
 *
 * <p>You can see the list of content-type parsers that are built-in to the
 * ParserRegistry class in {@link #buildDefaultParserMap()}.</p>
 *
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 * @see ContentType
 */
public class ParserRegistry {

    /**
     * The default parser used for unregistered content-types.  This is a copy
     * of {@link #parseStream(HttpResponse)}, which is like a no-op that just
     * returns the unaltered response stream.
     */
    protected final Closure<?> DEFAULT_PARSER = new MethodClosure(this, "parseStream");
    /**
     * The default charset to use when no charset is given in the Content-Type
     * header of a response.  This can be modifid via {@link #setDefaultCharset(String)}.
     */
    public static final String DEFAULT_CHARSET = "UTF-8";

    private Closure<?> defaultParser = DEFAULT_PARSER;
    private Map<String, Closure<?>> registeredParsers = buildDefaultParserMap();
    private static String defaultCharset = DEFAULT_CHARSET;

    protected static final Log log = LogFactory.getLog(ParserRegistry.class);

    /**
     * This CatalogResolver is static to avoid the overhead of re-parsing
     * the catalog definition file every time.  Unfortunately, there's no
     * way to share a single Catalog instance between resolvers.  The
     * {@link Catalog} class is technically not thread-safe, but as long as you
     * do not parse catalog files while using the resolver, it should be fine.
     */
    protected static CatalogResolver catalogResolver;

    static {
        CatalogManager catalogManager = new CatalogManager();
        catalogManager.setIgnoreMissingProperties(true);
        catalogManager.setUseStaticCatalog(false);
        catalogManager.setRelativeCatalogs(true);
        try {
            catalogResolver = new CatalogResolver(catalogManager);
            catalogResolver.getCatalog().parseCatalog(
                    ParserRegistry.class.getResource("/catalog/html.xml"));
        } catch (IOException ex) {
            LogFactory.getLog(ParserRegistry.class)
                    .warn("Could not resolve default XML catalog", ex);
        }
    }

    /**
     * Set the charset to use for parsing character streams when no charset
     * is given in the Content-Type header.
     *
     * @param charset the charset to use, or <code>null</code> to use
     *                {@link #DEFAULT_CHARSET}
     */
    public static void setDefaultCharset(String charset) {
        defaultCharset = charset == null ? DEFAULT_CHARSET : charset;
    }

    /**
     * Helper method to get the charset from the response.  This should be done
     * when manually parsing any text response to ensure it is decoded using the
     * correct charset. For instance:<pre>
     * Reader reader = new InputStreamReader( resp.getEntity().getContent(),
     *   ParserRegistry.getCharset( resp ) );</pre>
     *
     * @param resp
     */
    public static String getCharset(HttpResponse resp) {
        try {
            NameValuePair charset = resp.getEntity().getContentType()
                    .getElements()[0].getParameterByName("charset");

            if (charset == null || charset.getValue().trim().equals("")) {
                log.debug("Could not find charset in response; using " + defaultCharset);
                return defaultCharset;
            }

            return charset.getValue();
        } catch (RuntimeException ex) { // NPE or OOB Exceptions
            log.warn("Could not parse charset from content-type header in response");
            return Charset.defaultCharset().name();
        }
    }

    /**
     * Helper method to get the content-type string from the response
     * (no charset).
     *
     * @param resp
     */
    public static String getContentType(HttpResponse resp) {
        if (resp.getEntity() == null)
            throw new IllegalArgumentException("Response does not contain data");
        if (resp.getEntity().getContentType() == null)
            throw new IllegalArgumentException("Response does not have a content-type header");
        try {
            return resp.getEntity().getContentType().getElements()[0].getName();
        } catch (RuntimeException ex) {  // NPE or OOB Exceptions
            throw new IllegalArgumentException("Could not parse content-type from response");
        }
    }

    /**
     * Default parser used for binary data.  This simply returns the underlying
     * response InputStream.
     *
     * @param resp
     * @return an InputStream the binary response stream
     * @throws IllegalStateException
     * @throws IOException
     * @see ContentType#BINARY
     * @see HttpEntity#getContent()
     */
    public InputStream parseStream(HttpResponse resp) throws IOException {
        return resp.getEntity().getContent();
    }

    /**
     * Default parser used to handle plain text data.  The response text
     * is decoded using the charset passed in the response content-type
     * header.
     *
     * @param resp
     * @return
     * @throws UnsupportedEncodingException
     * @throws IllegalStateException
     * @throws IOException
     * @see ContentType#TEXT
     */
    public Reader parseText(HttpResponse resp) throws IOException {
        return new InputStreamReader(resp.getEntity().getContent(),
                ParserRegistry.getCharset(resp));
    }

    /**
     * Default parser used to decode a URL-encoded response.
     *
     * @param resp
     * @return
     * @throws IOException
     * @see ContentType#URLENC
     */
    public Map<String, String> parseForm(final HttpResponse resp) throws IOException {
        HttpEntity entity = resp.getEntity();
        /* URLEncodedUtils won't parse the content unless the content-type is
           application/x-www-form-urlencoded.  Since we want to be able to force
           parsing regardless of what the content-type header says, we need to
           'spoof' the content-type if it's not already acceptable. */
        if (!ContentType.URLENC.toString().equals(ParserRegistry.getContentType(resp))) {
            entity = new HttpEntityWrapper(entity) {
                @Override
                public org.apache.http.Header getContentType() {
                    String value = ContentType.URLENC.toString();
                    String charset = ParserRegistry.getCharset(resp);
                    if (charset != null) value += "; charset=" + charset;
                    return new BasicHeader("Content-Type", value);
                }

                ;
            };
        }
        List<NameValuePair> params = URLEncodedUtils.parse(entity);
        Map<String, String> paramMap = new HashMap<String, String>(params.size());
        for (NameValuePair param : params)
            paramMap.put(param.getName(), param.getValue());
        return paramMap;
    }

    /**
     * Parse an HTML document by passing it through the NekoHTML parser.
     *
     * @param resp HTTP response from which to parse content
     * @return the {@link GPathResult} from calling {@link XmlSlurper#parse(Reader)}
     * @throws IOException
     * @throws SAXException
     * @see ContentType#HTML
     * @see org.cyberneko.html.parsers.SAXParser
     * @see XmlSlurper#parse(Reader)
     */
    public GPathResult parseHTML(HttpResponse resp) throws IOException, SAXException {
        XMLReader p = new org.cyberneko.html.parsers.SAXParser();
        p.setEntityResolver(catalogResolver);
        return new XmlSlurper(p).parse(parseText(resp));
    }

    /**
     * Default parser used to decode an XML response.
     *
     * @param resp HTTP response from which to parse content
     * @return the {@link GPathResult} from calling {@link XmlSlurper#parse(Reader)}
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     * @see ContentType#XML
     * @see XmlSlurper#parse(Reader)
     */
    public GPathResult parseXML(HttpResponse resp) throws IOException, SAXException, ParserConfigurationException {
        XmlSlurper xml = new XmlSlurper();
        xml.setEntityResolver(catalogResolver);
        return xml.parse(parseText(resp));
    }

    /**
     * Default parser used to decode a JSON response.
     *
     * @param resp
     * @return
     * @throws IOException
     * @see ContentType#JSON
     */
    public Object parseJSON(HttpResponse resp) throws IOException {
        // there is a bug in the JsonSlurper.parse method...
        //String jsonTxt = DefaultGroovyMethods.getText( parseText( resp ) );
        return new JsonSlurper().parse(parseText(resp));
    }

    /**
     * <p>Returns a map of default parsers.  Override this method to change
     * what parsers are registered by default.  A 'parser' is really just a
     * closure that acceipts an {@link HttpResponse} instance and returns
     * some parsed data.  You can of course call
     * <code>super.buildDefaultParserMap()</code> and then add or remove
     * from that result as well.</p>
     *
     * <p>Default registered parsers are:
     * <ul>
     * <li>{@link ContentType#BINARY} :  {@link #parseStream(HttpResponse) parseStream()}</li>
     * <li>{@link ContentType#TEXT} :  {@link #parseText(HttpResponse) parseText()}</li>
     * <li>{@link ContentType#URLENC} :  {@link #parseForm(HttpResponse) parseForm()}</li>
     * <li>{@link ContentType#XML} :  {@link #parseXML(HttpResponse) parseXML()}</li>
     * <li>{@link ContentType#JSON} :  {@link #parseJSON(HttpResponse) parseJSON()}</li>
     * </ul>
     */
    protected Map<String, Closure<?>> buildDefaultParserMap() {
        Map<String, Closure<?>> parsers = new HashMap<String, Closure<?>>();

        parsers.put(ContentType.BINARY.toString(), new MethodClosure(this, "parseStream"));
        parsers.put(ContentType.TEXT.toString(), new MethodClosure(this, "parseText"));
        parsers.put(ContentType.URLENC.toString(), new MethodClosure(this, "parseForm"));
        parsers.put(ContentType.HTML.toString(), new MethodClosure(this, "parseHTML"));

        Closure<?> pClosure = new MethodClosure(this, "parseXML");
        for (String ct : ContentType.XML.getContentTypeStrings())
            parsers.put(ct, pClosure);

        pClosure = new MethodClosure(this, "parseJSON");
        for (String ct : ContentType.JSON.getContentTypeStrings())
            parsers.put(ct, pClosure);

        return parsers;
    }

    /**
     * Add a new XML catalog definiton to the static XML resolver catalog.
     * See the <a href='http://fisheye.codehaus.org/browse/gmod/httpbuilder/trunk/src/main/resources/catalog/html.xml?r=root:'>
     * HTTPBuilder source catalog</a> for an example.
     *
     * @param catalogLocation URL of a catalog definition file
     * @throws IOException if the given URL cannot be parsed or accessed for whatever reason.
     */
    public static void addCatalog(URL catalogLocation) throws IOException {
        catalogResolver.getCatalog().parseCatalog(catalogLocation);
    }

    /**
     * Access the default catalog used by all HTTPBuilder instances.
     *
     * @return the static {@link CatalogResolver} instance
     */
    public static CatalogResolver getCatalogResolver() {
        return catalogResolver;
    }

    /**
     * Get the default parser used for unregistered content-types.
     *
     * @return
     */
    public Closure<?> getDefaultParser() {
        return this.defaultParser;
    }

    /**
     * Set the default parser used for unregistered content-types.
     *
     * @param defaultParser if
     */
    public void setDefaultParser(Closure<?> defaultParser) {
        if (defaultParser == null) this.defaultParser = DEFAULT_PARSER;
        this.defaultParser = defaultParser;
    }

    /**
     * Retrieve a parser for the given response content-type string.  This
     * is called by HTTPBuildre to retrieve the correct parser for a given
     * content-type.  The parser is then used to decode the response data prior
     * to passing it to a response handler.
     *
     * @param contentType
     * @return parser that can interpret the given response content type,
     * or the default parser if no parser is registered for the given
     * content-type.
     */
    public Closure<?> getAt(Object contentType) {
        if (contentType == null) return defaultParser;

        String ct = contentType.toString();
        int idx = ct.indexOf(';');
        if (idx > 0) ct = ct.substring(0, idx);

        Closure<?> parser = registeredParsers.get(ct);
        if (parser != null) return parser;

        log.warn("Cannot find parser for content-type: " + ct
                + " -- using default parser.");
        return defaultParser;
    }

    /**
     * Register a new parser for the given content-type.  The parser closure
     * should accept an {@link HttpResponse} argument and return a type suitable
     * to be passed as the 'parsed data' argument of a
     * {@link RequestConfigDelegate#getResponse() response handler} closure.
     *
     * @param contentType <code>content-type</code> string
     * @param value       code that will parse the HttpResponse and return parsed
     *                    data to the response handler.
     */
    public void putAt(Object contentType, Closure<?> value) {
        if (contentType instanceof ContentType) {
            for (String ct : ((ContentType) contentType).getContentTypeStrings())
                this.registeredParsers.put(ct, value);
        } else this.registeredParsers.put(contentType.toString(), value);
    }

    /**
     * Alias for {@link #getAt(Object)} to allow property-style access.
     *
     * @param key content-type string
     * @return
     */
    public Closure<?> propertyMissing(Object key) {
        return this.getAt(key);
    }

    /**
     * Alias for {@link #putAt(Object, Closure)} to allow property-style access.
     *
     * @param key   content-type string
     * @param value parser closure
     */
    public void propertyMissing(Object key, Closure<?> value) {
        this.putAt(key, value);
    }

    /**
     * Iterate over the entire parser map
     *
     * @return
     */
    public Iterator<Map.Entry<String, Closure<?>>> iterator() {
        return this.registeredParsers.entrySet().iterator();
    }
}

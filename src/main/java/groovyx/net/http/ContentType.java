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

import java.util.Iterator;

import org.apache.commons.collections.iterators.ArrayIterator;

/**
 * Enumeration of common <a href="http://www.iana.org/assignments/media-types/">IANA</a>
 * content-types.  This may be used to specify a request or response
 * content-type more easily than specifying the full string each time.  i.e.
 * <pre>
 * http.request( GET, JSON ) {...}</pre>
 *
 * Is roughly equivalent to:
 * <pre>
 * http.request( GET, 'application/json' )</pre>
 *
 * The only difference being, equivalent content-types (i.e.
 * <code>application/xml</code> and <code>text/xml</code> are all added to the
 * request's <code>Accept</code> header.  By default, all equivalent content-types
 * are handled the same by the {@link EncoderRegistry} and {@link ParserRegistry}
 * as well.
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public enum ContentType {
    /** <code>&#42;/*</code> */
    ANY("*/*"),
    /** <code>text/plain</code> */
    TEXT("text/plain"),
    /**
     * <ul>
     *  <li><code>application/json</code></li>
     *  <li><code>application/javascript</code></li>
     *  <li><code>text/javascript</code></li>
     * </ul>
     */
    JSON("application/json","application/javascript","text/javascript"),
    /**
     * <ul>
     *  <li><code>application/xml</code></li>
     *  <li><code>text/xml</code></li>
     *  <li><code>application/xhtml+xml</code></li>
     *  <li><code>application/atom+xml</code></li>
     * </ul>
     */
    XML("application/xml","text/xml","application/xhtml+xml","application/atom+xml"),
    /** <code>text/html</code> */
    HTML("text/html"),
    /** <code>application/x-www-form-urlencoded</code> */
    URLENC("application/x-www-form-urlencoded"),
    /** <code>application/octet-stream</code> */
    BINARY("application/octet-stream"),
    /** <code>multipart/form-data</code> */
    MULTIPART("multipart/form-data");

    private final String[] ctStrings;
    public String[] getContentTypeStrings() { return ctStrings; }
    @Override public String toString() { return ctStrings[0]; }

    /**
     * Builds a string to be used as an HTTP <code>Accept</code> header
     * value, i.e. "application/xml, text/xml"
     * @return
     */
    @SuppressWarnings("unchecked")
    public String getAcceptHeader() {
        Iterator<String> iter = new ArrayIterator(ctStrings);
        StringBuilder sb = new StringBuilder();
        while ( iter.hasNext() ) {
            sb.append( iter.next() );
            if ( iter.hasNext() ) sb.append( ", " );
        }
        return sb.toString();
    }

    private ContentType( String... contentTypes ) {
        this.ctStrings = contentTypes;
    }
}

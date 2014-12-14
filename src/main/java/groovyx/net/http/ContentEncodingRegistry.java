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

import groovyx.net.http.ContentEncoding.Type;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps track of available content-encoding handlers.
 *
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public class ContentEncodingRegistry {

    protected Map<String, ContentEncoding> availableEncoders = getDefaultEncoders();

    /**
     * This implementation adds a {@link GZIPEncoding} and {@link DeflateEncoding}
     * handler to the registry.  Override this method to provide a different set
     * of defaults.
     *
     * @return a map to content-encoding strings to {@link ContentEncoding} handlers.
     */
    protected Map<String, ContentEncoding> getDefaultEncoders() {
        Map<String, ContentEncoding> map = new HashMap<String, ContentEncoding>();
        map.put(Type.GZIP.toString(), new GZIPEncoding());
        map.put(Type.DEFLATE.toString(), new DeflateEncoding());
        return map;
    }

    /**
     * Add the request and response interceptors to the {@link HttpClient},
     * which will provide transparent decoding of the given content-encoding
     * types.  This method is called by HTTPBuilder and probably should not need
     * be modified by sub-classes.
     *
     * @param client    client on which to set the request and response interceptors
     * @param encodings encoding name (either a {@link ContentEncoding.Type} or
     *                  a <code>content-encoding</code> string.
     */
    void setInterceptors(final HttpClientBuilder builder, Object... encodings) {
		for (Object encName : encodings) {
			ContentEncoding enc = availableEncoders.get(encName.toString());
			if (enc != null) {
				builder.addInterceptorFirst(enc.getRequestInterceptor());
				builder.addInterceptorFirst(enc.getResponseInterceptor());
			}
		}
    }
}

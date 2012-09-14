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

/**
 * Wraps an error response in an exception for flow control purposes.  That is,
 * you can still inspect response headers, but in a
 * <code>catch( HttpResponseException ex ) {  }</code> block.
 *
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 * @since 0.5
 */
public class HttpResponseException extends org.apache.http.client.HttpResponseException {

    private static final long serialVersionUID = -34809347677236L;

    HttpResponseDecorator response;

    public HttpResponseException( HttpResponseDecorator resp ) {
        super( resp.getStatusLine().getStatusCode(),
                resp.getStatusLine().getReasonPhrase() );
        this.response = resp;
    }

    public HttpResponseDecorator getResponse() {
        return response;
    }
}

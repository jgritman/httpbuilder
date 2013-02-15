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

import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * Enumeration of valid HTTP methods that may be used in a
 * {@link HTTPBuilder#request(Method, groovy.lang.Closure) request} call.
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public enum Method {
    GET( HttpGet.class ),
    PUT( HttpPut.class ),
    POST( HttpPost.class ),
    DELETE( HttpDelete.class ),
    HEAD( HttpHead.class ),
    PATCH( HttpPatch.class );

    private final Class<? extends HttpRequestBase> requestType;

    /**
     * Get the HttpRequest class that represents this request type.
     * @return a non-abstract class that implements {@link HttpRequest}
     */
    public Class<? extends HttpRequestBase> getRequestType() { return this.requestType; }

    private Method( Class<? extends HttpRequestBase> type ) {
        this.requestType = type;
    }
}

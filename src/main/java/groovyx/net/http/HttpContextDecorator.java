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

import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

/**
 * HttpContext stores many transient properties of an HTTP request.
 * This class adds Groovy convenience methods.  For a list of many
 * common properties stored in the HttpContext, see:
 * <ul>
 * <li>{@link org.apache.http.protocol.ExecutionContext}</li>
 * <li>{@link org.apache.http.client.protocol.ClientContext}</li>
 * </ul>
 *
 * @author tnichols
 */
public class HttpContextDecorator implements HttpContext {

    protected HttpContext delegate;

    public HttpContextDecorator() {
        this.delegate = new BasicHttpContext();
    }

    public HttpContextDecorator(HttpContext delegate) {
        this.delegate = new BasicHttpContext(delegate);
    }

    /**
     * Groovy support for the index [] operator
     *
     * @param name
     * @return
     */
    public Object getAt(String name) {
        return this.getAttribute(name);
    }

    /**
     * Groovy support for the index [] operator
     *
     * @param name
     * @param val
     */
    public void setAt(String name, Object val) {
        this.setAttribute(name, val);
    }

    /* (non-Javadoc)
     * @see org.apache.http.protocol.HttpContext#getAttribute(java.lang.String)
     */
    public Object getAttribute(String name) {
        return this.delegate.getAttribute(name);
    }

    /* (non-Javadoc)
     * @see org.apache.http.protocol.HttpContext#removeAttribute(java.lang.String)
     */
    public Object removeAttribute(String name) {
        return this.delegate.removeAttribute(name);
    }

    /* (non-Javadoc)
     * @see org.apache.http.protocol.HttpContext#setAttribute(java.lang.String, java.lang.Object)
     */
    public void setAttribute(String name, Object val) {
        this.delegate.setAttribute(name, val);
    }
}

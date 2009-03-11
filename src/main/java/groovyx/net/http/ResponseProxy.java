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

import groovy.util.Proxy;

import java.util.Iterator;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;

/**
 * This class is a wrapper for {@link HttpResponse}, which allows for 
 * simplified header access, as well as carrying the auto-parsed response data.
 * (see {@link HTTPBuilder#parseResponse(HttpResponse, Object)}).
 * 
 * @author <a href='mailto:tnichols@enernoc.com'>Tom Nichols</a>
 * @since 0.5
 */
public class ResponseProxy extends Proxy {
	
	HeadersProxy headers = null;
	HttpResponse responseBase;
	Object responseData;
	
	public ResponseProxy( HttpResponse base, Object parsedResponse ) {
		super.setAdaptee( base );
		this.responseBase = base;
		this.responseData = parsedResponse;
	}
	
	/** 
	 * Return a {@link HeadersProxy}, which provides a more Groovy API for 
	 * accessing response headers.
	 * @return the headers for this response
	 */
	public HeadersProxy getHeaders() {
		if ( headers == null ) headers = new HeadersProxy();
		return headers;
	}
	
	/**
	 * Quickly determine if the request resulted in an error code.
	 * @return true if the response code is within the range of 
	 *   {@link Status#SUCCESS}
	 */
	public boolean isSuccess() {
		return Status.find( getStatus() ) == Status.SUCCESS;
	}
	
	/**
	 * Get the response status code.
	 * @see StatusLine#getStatusCode()
	 * @return the HTTP response code.
	 */
	public int getStatus() {
		return responseBase.getStatusLine().getStatusCode();
	}
	
	/**
	 * Get the content-type for this response.
	 * @see ParserRegistry#getContentType(HttpResponse)
	 * @return the content-type string, without any charset information.
	 */
	public String getContentType() {
		return ParserRegistry.getContentType( responseBase );
	}
	
	/**
	 * Return the parsed data from this response body.
	 * @return the parsed response object, or <code>null</code> if the response
	 * does not contain any data.
	 */
	public Object getData() { return this.responseData; }
	
	// TODO quick access to location header for REST?
	
	/**
	 * 
	 */
	public class HeadersProxy extends Proxy implements Iterable<Header> {
		
		/**
		 * Access the named header, using bracket form.  For example,
		 * <code>response.headers['Content-Encoding']</code>
		 * @see HttpResponse#getFirstHeader(String)
		 * @param name header name, e.g. <code>Content-Type<code>
		 * @return the {@link Header}, or <code>null</code> if it does not exist
		 *  in this response 
		 */
		public Header getAt( String name ) {
			return responseBase.getFirstHeader( name );
		}
		
		/**
		 * Allow property-style access to header values. 
		 * @see #getAt(String)
		 * @param name header name, e.g. <code>Content-Type<code>
		 * @return the {@link Header}, or <code>null</code> if it does not exist
		 *  in this response 
		 */
		protected Object propertyMissing( String name ) {
			return getAt( name );
		}		
		
		/**
		 * Used to allow Groovy iteration methods over the response headers.
		 * For example:
		 * <pre>response.headers.each {
		 *   println "${it.name} : ${it.value}"
		 * }</pre>
		 */
		@Override public Iterator<Header> iterator() {
			return responseBase.headerIterator();
		}
	}
}
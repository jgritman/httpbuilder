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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.MethodClosure;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * This implementation makes all requests asynchronous by submitting jobs to a 
 * {@link ThreadPoolExecutor}.  All request methods (including <code>get</code> 
 * and <code>post</code>) return a {@link Future} instance, whose 
 * {@link Future#get() get} method will provide access to whatever value was 
 * returned from the response handler closure.
 *  
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public class AsyncHTTPBuilder extends HTTPBuilder {

	/**
	 * Default pool size is one is not supplied in the constructor.
	 */
	public static final int DEFAULT_POOL_SIZE = 4;
	
	protected final ThreadPoolExecutor threadPool = 
		(ThreadPoolExecutor)Executors.newCachedThreadPool();
	
	/**
	 * Accepts the following named parameters:
	 * <dl>
	 *  <dt>poolSize</dt><dd>Max number of concurrent requests</dd>
	 *  <dt>url</dt><dd>Default request URL</dd>
	 *  <dt>contentType</dt><dd>Default content type for requests and responses</dd>
	 * </dl>
	 */
	public AsyncHTTPBuilder( Map<String, ?> args ) throws URISyntaxException {
		super();
		int poolSize = DEFAULT_POOL_SIZE;
		if ( args != null ) { 
			Object poolSzArg = args.get("poolSize");
			if ( poolSzArg != null ) poolSize = Integer.parseInt( poolSzArg.toString() );
			
			if ( args.get( "url" ) != null ) throw new IllegalArgumentException(
				"The 'url' parameter is deprecated; use 'uri' instead" );
			Object defaultURI = args.get("uri");
			if ( defaultURI != null ) super.setUri(defaultURI);
	
			Object defaultContentType = args.get("contentType");
			if ( defaultContentType != null ) 
				super.setContentType(defaultContentType);
		}
		this.initThreadPools( poolSize );
	}
	
	/**
	 * Submits a {@link Callable} instance to the job pool, which in turn will 
	 * call {@link HTTPBuilder#doRequest(RequestConfigDelegate)} in an asynchronous 
	 * thread.  The {@link Future} instance returned by this value (which in 
	 * turn should be returned by any of the public <code>request</code> methods
	 * (including <code>get</code> and <code>post</code>) may be used to 
	 * retrieve whatever value may be returned from the executed response 
	 * handler closure. 
	 */
	@Override
	protected Future<?> doRequest( final RequestConfigDelegate delegate ) {
		return threadPool.submit( new Callable<Object>() {
			/*@Override*/ public Object call() throws Exception {
				try {
					return doRequestSuper(delegate);
				}
				catch( Exception ex ) {
					log.error( "Exception thrown from request delegate: " + 
							delegate, ex );
					throw ex;
				}
			}
		});
	}
	
	/*
	 * Because we can't call "super.doRequest" from within the anonymous 
	 * Callable subclass.
	 */
	private Object doRequestSuper( RequestConfigDelegate delegate ) throws IOException {
		return super.doRequest(delegate);
	}
	
	/**
	 * Initializes threading parameters for the HTTPClient's 
	 * {@link ThreadSafeClientConnManager}, and this class' ThreadPoolExecutor. 
	 */
	protected void initThreadPools( final int poolSize ) {
		if (poolSize < 1) throw new IllegalArgumentException("poolSize may not be < 1");
		// Create and initialize HTTP parameters
		HttpParams params = client != null ? client.getParams()
				: new BasicHttpParams();
		ConnManagerParams.setMaxTotalConnections(params, poolSize);
		ConnManagerParams.setMaxConnectionsPerRoute(params,
				new ConnPerRouteBean(poolSize));

		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);

		// Create and initialize scheme registry
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register( new Scheme( "http", 
				PlainSocketFactory.getSocketFactory(), 80 ) );
		schemeRegistry.register( new Scheme( "https", 
				SSLSocketFactory.getSocketFactory(), 443));

		ClientConnectionManager cm = new ThreadSafeClientConnManager(
				params, schemeRegistry );
		super.client = new DefaultHttpClient( cm, params );

		/* Although the thread pool is flexible, it cannot become bigger than
		 * the max size of the connection pool-- otherwise threads will be
		 * created in this pool for new jobs, but they will all block when
		 * waiting for a free connection to send the request.
		 */
		this.threadPool.setMaximumPoolSize(poolSize);
	}
	
	@Override
	protected Object defaultSuccessHandler( HttpResponse resp, Object parsedData )
			throws IOException {
		return super.defaultSuccessHandler( resp, parsedData );
	}
	
	@Override
	protected void defaultFailureHandler( HttpResponseDecorator resp )
			throws HttpResponseException {
		super.defaultFailureHandler( resp );
	}
	
	/**
	 * <p>Access the underlying threadpool to adjust things like job timeouts.</p>  
	 * 
	 * <p>Note that this is not the same thread pool used by the HttpClient's 
	 * {@link ThreadSafeClientConnManager}.  Therefore, increasing the 
	 * {@link ThreadPoolExecutor#setMaximumPoolSize(int) maximum pool size} will
	 * not in turn increase the number of possible concurrent requests.  It will
	 * simply cause more requests to be <i>attempted</i> which will then simply
	 * block while waiting for an available connection.</p>
	 * 
	 * <p>Since {@link ThreadSafeClientConnManager} has no public mechanism to 
	 * adjust its pool size, the value 
	 * @return
	 */
	public ThreadPoolExecutor getThreadPoolExecutor() {
		return this.threadPool;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override public void shutdown() {
		super.shutdown(); 
		this.threadPool.shutdown();
	}
	
	/**
	 * {@inheritDoc}
	 * @see #shutdown()
	 */
	@Override protected void finalize() throws Throwable {
		this.shutdown();
		super.finalize();
	}
}
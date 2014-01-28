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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import org.apache.http.params.HttpConnectionParams;
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

    protected ExecutorService threadPool;
//      = (ThreadPoolExecutor)Executors.newCachedThreadPool();

    /**
     * Accepts the following named parameters:
     * <dl>
     *  <dt>threadPool</dt><dd>Custom {@link ExecutorService} instance for
     *      running submitted requests.  If this is an instance of {@link ThreadPoolExecutor},
     *      the poolSize will be determined by {@link ThreadPoolExecutor#getMaximumPoolSize()}.
     *      The default threadPool uses an unbounded queue to accept an unlimited
     *      number of requests.</dd>
     *  <dt>poolSize</dt><dd>Max number of concurrent requests</dd>
     *  <dt>uri</dt><dd>Default request URI</dd>
     *  <dt>contentType</dt><dd>Default content type for requests and responses</dd>
     *  <dt>timeout</dt><dd>Timeout in milliseconds to wait for a connection to
     *      be established and request to complete.</dd>
     * </dl>
     */
    public AsyncHTTPBuilder( Map<String, ?> args ) throws URISyntaxException {
        int poolSize = DEFAULT_POOL_SIZE;
        ExecutorService threadPool = null;
        if ( args != null ) {
            threadPool = (ExecutorService)args.remove( "threadPool" );

            if ( threadPool instanceof ThreadPoolExecutor )
                poolSize = ((ThreadPoolExecutor)threadPool).getMaximumPoolSize();

            Object poolSzArg = args.remove("poolSize");
            if ( poolSzArg != null ) poolSize = Integer.parseInt( poolSzArg.toString() );

            if ( args.containsKey( "url" ) ) throw new IllegalArgumentException(
                "The 'url' parameter is deprecated; use 'uri' instead" );
            Object defaultURI = args.remove("uri");
            if ( defaultURI != null ) super.setUri(defaultURI);

            Object defaultContentType = args.remove("contentType");
            if ( defaultContentType != null )
                super.setContentType(defaultContentType);

            Object timeout = args.remove( "timeout" );
            if ( timeout != null ) setTimeout( (Integer) timeout );

            if ( args.size() > 0 ) {
                String invalidArgs = "";
                for ( String k : args.keySet() ) invalidArgs += k + ",";
                throw new IllegalArgumentException("Unexpected keyword args: " + invalidArgs);
            }
        }
        this.initThreadPools( poolSize, threadPool );
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
                    log.info( "Exception thrown from response delegate: " + delegate, ex );
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
    protected void initThreadPools( final int poolSize, final ExecutorService threadPool ) {
        if (poolSize < 1) throw new IllegalArgumentException("poolSize may not be < 1");
        // Create and initialize HTTP parameters
        HttpParams params = super.getClient().getParams();
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
        setClient(new DefaultHttpClient( cm, params ));

        this.threadPool = threadPool != null ? threadPool :
            new ThreadPoolExecutor( poolSize, poolSize, 120, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>() );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object defaultSuccessHandler( HttpResponseDecorator resp, Object parsedData )
            throws ResponseParseException {
        return super.defaultSuccessHandler( resp, parsedData );
    }

    /**
     * For 'failure' responses (e.g. a 404), the exception will be wrapped in
     * a {@link ExecutionException} and held by the {@link Future} instance.
     * The exception is then re-thrown when calling {@link Future#get()
     * future.get()}.  You can access the original exception (e.g. an
     * {@link HttpResponseException}) by calling <code>ex.getCause()</code>.
     *
     */
    @Override
    protected void defaultFailureHandler( HttpResponseDecorator resp )
            throws HttpResponseException {
        super.defaultFailureHandler( resp );
    }

    /**
     * This timeout is used for both the time to wait for an established
     * connection, and the time to wait for data.
     * @see HttpConnectionParams#setSoTimeout(HttpParams, int)
     * @see HttpConnectionParams#setConnectionTimeout(HttpParams, int)
     * @param timeout time to wait in milliseconds.
     */
    public void setTimeout( int timeout ) {
        HttpConnectionParams.setConnectionTimeout( super.getClient().getParams(), timeout );
        HttpConnectionParams.setSoTimeout( super.getClient().getParams(), timeout );
        /* this will cause a thread waiting for an available connection instance
         * to time-out   */
//      ConnManagerParams.setTimeout( super.getClient().getParams(), timeout );
    }

    /**
     * Get the timeout in for establishing an HTTP connection.
     * @return timeout in milliseconds.
     */
    public int getTimeout() {
        return HttpConnectionParams.getConnectionTimeout( super.getClient().getParams() );
    }

    /**
     * <p>Access the underlying threadpool to adjust things like job timeouts.</p>
     *
     * <p>Note that this is not the same pool used by the HttpClient's
     * {@link ThreadSafeClientConnManager}.  Therefore, increasing the
     * {@link ThreadPoolExecutor#setMaximumPoolSize(int) maximum pool size} will
     * not in turn increase the number of possible concurrent requests.  It will
     * simply cause more requests to be <i>attempted</i> which will then simply
     * block while waiting for a free connection.</p>
     *
     * @return the service used to execute requests.  By default this is a
     * {@link ThreadPoolExecutor}.
     */
    public ExecutorService getThreadExecutor() {
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

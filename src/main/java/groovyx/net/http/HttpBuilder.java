package groovyx.net.http;

import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.HttpResponse;
import java.util.function.Function;
import org.apache.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.Header;
import groovy.lang.Closure;
import org.apache.http.util.EntityUtils;
import java.io.IOException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import groovy.lang.DelegatesTo;
import org.apache.http.client.methods.*;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;

//TODO: Write script wire for default handlers
//TODO: Test basic and async get
public class HttpBuilder {

    private static final Logger log = LoggerFactory.getLogger(HttpBuilder.class);
    
    private static class Handler implements ResponseHandler<Object> {

        private final Effective config;
        
        public Handler(final Effective config) {
            this.config = config;
        }
        
        private String extractContentType(final HttpEntity entity) {
            if(entity == null) {
                return null;
            }

            final Header header = entity.getContentType();
            if(header == null) {
                return null;
            }

            return header.getValue();
        }

        private Function<HttpResponse,Object> findParser(final String contentType) {
            final Function<HttpResponse,Object> found = config.effectiveParser(contentType);
            return found == null ? NativeHandlers.Parsers::stream : found;
        }

        private Object[] closureArgs(final Closure<Object> closure, final HttpResponse response, final Object o) {
            final int size = closure.getMaximumNumberOfParameters();
            final Object[] args = new Object[size];
            if(size >= 1) {
                args[0] = response;
            }

            if(size >= 2) {
                args[1] = o;
            }

            return args;
        }

        private void cleanup(final HttpResponse response) {
            final HttpEntity entity = response.getEntity();
            try {
                if(entity != null) {
                    EntityUtils.consume(entity);
                }
            }
            catch(IOException ioe) {
                if(log.isWarnEnabled()) {
                    log.warn("Could not fully consume http entity", ioe);
                }
            }
        }

        public Object handleResponse(final HttpResponse response) {
            try {
                final int status = response.getStatusLine().getStatusCode();
                final HttpEntity entity = response.getEntity();
                final String contentType = extractContentType(entity);
                final Function<HttpResponse,Object> parser = findParser(contentType);
                final Closure<Object> action = config.getResp().effectiveAction(status);
                final Object o = parser.apply(response);
                return action.call(closureArgs(action, response, o));
            }
            finally {
                cleanup(response);
            }
        }
    }

    private static class SingleThreaded implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }

    final private CloseableHttpClient client;
    final private AbstractHttpConfig config;
    final private Executor executor;
    
    public HttpBuilder(final CloseableHttpClient client, final AbstractHttpConfig config, final Executor executor) {
        this.client = client;
        this.config = config;
        this.executor = executor;
    }

    public void close() {
        try {
            client.close();
        }
        catch(IOException ioe) {
            if(log.isWarnEnabled()) {
                log.warn("Error in closing http client", ioe);
            }
        }
    }

    public static HttpBuilder singleThreaded() {
        return new HttpBuilder(HttpClients.createDefault(), AbstractHttpConfig.classLevel(false), new SingleThreaded());
    }

    public static HttpBuilder multiThreaded(final int max, final Executor executor) {
        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(max);
        cm.setDefaultMaxPerRoute(max);
        
        final CloseableHttpClient client = HttpClients.custom()
            .setConnectionManager(cm)
            .build();
        
        return new HttpBuilder(client, AbstractHttpConfig.classLevel(true), executor);
    }

    private AbstractHttpConfig configureRequest(final Closure closure) {
        final AbstractHttpConfig myConfig = AbstractHttpConfig.requestLevel(config);
        closure.setDelegate(myConfig);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return myConfig;
    }

    private Object exec(final HttpUriRequest request, final AbstractHttpConfig requestConfig) {
        try {
            return client.execute(request, new Handler(requestConfig));
        }
        catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
    
    public Object get(@DelegatesTo(HttpConfig.class) final Closure closure) {
        final AbstractHttpConfig requestConfig = configureRequest(closure);
        return exec(new HttpGet(requestConfig.getReq().effectiveUri().toURI()), requestConfig);
    }

    public <T> T get(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return type.cast(get(closure));
    }

    public CompletableFuture<Object> getAsync(@DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> get(closure), executor);
    }

    public <T> CompletableFuture<T> getAsync(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> get(type, closure), executor);
    }
}

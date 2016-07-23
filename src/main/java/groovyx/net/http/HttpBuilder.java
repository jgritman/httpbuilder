package groovyx.net.http;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.codehaus.groovy.runtime.MethodClosure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpBuilder {

    private static final Logger log = LoggerFactory.getLogger(HttpBuilder.class);
    
    private static class Handler implements ResponseHandler<Object> {

        private final ChainedHttpConfig config;
        private String contentType = "application/octet-stream";
        private Charset charset = StandardCharsets.UTF_8;
        
        public Handler(final ChainedHttpConfig config) {
            this.config = config;
        }
        
        private void processContentType(final HttpEntity entity) {
            if(entity == null) {
                return;
            }
            
            final Header header = entity.getContentType();
            if(header == null) {
                return;
            }

            final HeaderElement[] elements = header.getElements();
            if(elements == null) {
                contentType = header.getValue();
            }

            HeaderElement element = elements[0];
            contentType = element.getName();
            if(element.getParameters() != null && element.getParameters().length != 0) {
                final NameValuePair nvp = element.getParameter(0);
                if(nvp.getName().toLowerCase().equals("charset")) {
                    charset = Charset.forName(nvp.getValue());
                }
            }
        }

        private Function<HttpResponse,Object> findParser(final String contentType) {
            final Function<HttpResponse,Object> found = config.getChainedResponse().actualParser(contentType);
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
                processContentType(entity);
                final Function<HttpResponse,Object> parser = findParser(contentType);
                final Closure<Object> action = config.getChainedResponse().actualAction(status);
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

    final private CookieStore cookieStore;
    final private CloseableHttpClient client;
    final private ChainedHttpConfig config;
    final private Executor executor;
    
    protected HttpBuilder(final CloseableHttpClient client, final CookieStore cookieStore,
                          final ChainedHttpConfig config, final Executor executor) {
        this.client = client;
        this.cookieStore = cookieStore;
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

    public static HttpBuilder configure() {
        return configure(NO_OP);
    }
    
    public static HttpBuilder configure(@DelegatesTo(HttpObjectConfig.class) final Closure closure) {
        HttpObjectConfigImpl impl = new HttpObjectConfigImpl();
        closure.setDelegate(impl);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return impl.build();
    }

    private ChainedHttpConfig configureRequest(final Closure closure) {
        final ChainedHttpConfig myConfig = HttpConfigs.requestLevel(config);
        closure.setDelegate(myConfig);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return myConfig;
    }

    private int port(final URI uri) {
        if(uri.getPort() != -1) {
            return uri.getPort();
        }

        if(uri.getScheme().startsWith("https")) {
            return 443;
        }

        return 80;
    }

    private void basicAuth(final HttpClientContext c, final HttpConfig.Auth auth, final URI uri) {
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(new AuthScope(uri.getHost(), port(uri)),
                                new UsernamePasswordCredentials(auth.getUser(), auth.getPassword()));
        c.setCredentialsProvider(provider);
    }

    private void digestAuth(final HttpClientContext c, final HttpConfig.Auth auth, final URI uri) {
        basicAuth(c, auth, uri);
    }

    private HttpClientContext context(final ChainedHttpConfig requestConfig) {
        final HttpClientContext c = HttpClientContext.create();
        final HttpConfig.Auth auth = requestConfig.getChainedRequest().actualAuth();
        
        if(auth != null) {
            final URI uri = requestConfig.getRequest().getUri().toURI();
            if(auth.getAuthType() == HttpConfig.AuthType.BASIC) {
                basicAuth(c, auth, uri);
            }
            else if(auth.getAuthType() == HttpConfig.AuthType.DIGEST) {
                digestAuth(c, auth, uri);
            }
        }
        
        return c;
    }

    private Object exec(final HttpUriRequest request, final ChainedHttpConfig requestConfig) {
        try {
            return client.execute(request, new Handler(requestConfig), context(requestConfig));
        }
        catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private HttpEntity entity(final ChainedHttpConfig config) {
        final ChainedHttpConfig.ChainedRequest cr = config.getChainedRequest();
        final String contentType = cr.actualContentType();
        if(contentType == null) {
            throw new IllegalStateException("Found request body, but content type is undefined");
        }
        
        final Function<ChainedHttpConfig.ChainedRequest,HttpEntity> encoder = cr.actualEncoder(contentType);
        if(encoder == null) {
            throw new IllegalStateException("Found body, but did not find encoder");
        }

        return encoder.apply(cr);
    }

    private <T extends HttpUriRequest> T addHeaders(final ChainedHttpConfig.ChainedRequest cr, final T message) {
        for(Map.Entry<String,String> entry : cr.actualHeaders(new LinkedHashMap<>()).entrySet()) {
            message.addHeader(entry.getKey(), entry.getValue());
        }

        //technically cookies are headers, so add them here
        List<Cookie> cookies = cr.actualCookies(new ArrayList());
        for(Cookie cookie : cookies) {
            cookieStore.addCookie(cookie);
        }

        return message;
    }

    public static final void noOp() { }
    private static final Closure NO_OP = new MethodClosure(HttpBuilder.class, "noOp");

    public Object get() {
        return get(NO_OP);
    }
    
    public Object get(@DelegatesTo(HttpConfig.class) final Closure closure) {
        final ChainedHttpConfig requestConfig = configureRequest(closure);
        final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
        return exec(addHeaders(cr, new HttpGet(cr.getUri().toURI())), requestConfig);
    }

    public <T> T get(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return type.cast(get(closure));
    }

    public CompletableFuture<Object> getAsync() {
        return CompletableFuture.supplyAsync(() -> get(), executor);
    }
    
    public CompletableFuture<Object> getAsync(@DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> get(closure), executor);
    }

    public <T> CompletableFuture<T> getAsync(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> get(type, closure), executor);
    }

    public Object head() {
        return head(NO_OP);
    }
    
    public Object head(@DelegatesTo(HttpConfig.class) final Closure closure) {
        final ChainedHttpConfig requestConfig = configureRequest(closure);
        final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
        return exec(addHeaders(cr, new HttpHead(cr.getUri().toURI())), requestConfig);
    }

    public <T> T head(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return type.cast(head(closure));
    }

    public CompletableFuture<Object> headAsync() {
        return CompletableFuture.supplyAsync(() -> head(), executor);
    }
    
    public CompletableFuture<Object> headAsync(@DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> head(closure), executor);
    }

    public <T> CompletableFuture<T> headAsync(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> head(type, closure), executor);
    }
    
    public Object post() {
        return post(NO_OP);
    }

    public Object post(@DelegatesTo(HttpConfig.class) final Closure closure) {
        final ChainedHttpConfig requestConfig = configureRequest(closure);
        final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
        final HttpPost post = addHeaders(cr, new HttpPost(cr.getUri().toURI()));
        if(cr.actualBody() != null) {
            post.setEntity(entity(requestConfig));
        }
        
        return exec(post, requestConfig);
    }

    public <T> T post(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return type.cast(post(closure));
    }

    public CompletableFuture<Object> postAsync() {
        return CompletableFuture.supplyAsync(() -> post(NO_OP), executor);
    }

    public Object postAsync(@DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> post(closure), executor);
    }
    
    public <T> CompletableFuture<T> postAsync(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> post(type, closure), executor);
    }

    public Object put() {
        return put(NO_OP);
    }

    public Object put(@DelegatesTo(HttpConfig.class) final Closure closure) {
        final ChainedHttpConfig requestConfig = configureRequest(closure);
        final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
        final HttpPut put = addHeaders(cr, new HttpPut(cr.getUri().toURI()));
        if(cr.actualBody() != null) {
            put.setEntity(entity(requestConfig));
        }
        
        return exec(put, requestConfig);
    }

    public <T> T put(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return type.cast(put(closure));
    }

    public CompletableFuture<Object> putAsync() {
        return CompletableFuture.supplyAsync(() -> put(NO_OP), executor);
    }

    public Object putAsync(@DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> put(closure), executor);
    }
    
    public <T> CompletableFuture<T> putAsync(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> put(type, closure), executor);
    }

    //deletes
    public Object delete() {
        return delete(NO_OP);
    }

    public Object delete(@DelegatesTo(HttpConfig.class) final Closure closure) {
        final ChainedHttpConfig requestConfig = configureRequest(closure);
        final ChainedHttpConfig.ChainedRequest cr = requestConfig.getChainedRequest();
        final HttpDelete del = addHeaders(cr, new HttpDelete(cr.getUri().toURI()));
        return exec(del, requestConfig);
    }

    public <T> T delete(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return type.cast(delete(closure));
    }

    public CompletableFuture<Object> deleteAsync() {
        return CompletableFuture.supplyAsync(() -> delete(NO_OP), executor);
    }

    public Object deleteAsync(@DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> delete(closure), executor);
    }
    
    public <T> CompletableFuture<T> deleteAsync(final Class<T> type, @DelegatesTo(HttpConfig.class) final Closure closure) {
        return CompletableFuture.supplyAsync(() -> delete(type, closure), executor);
    }
}

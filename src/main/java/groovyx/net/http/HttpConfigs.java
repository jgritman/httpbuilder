package groovyx.net.http;

import java.util.Date;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyShell;
import groovy.transform.TypeChecked;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import static groovyx.net.http.ChainedHttpConfig.*;

public class HttpConfigs {

    public static class BasicAuth implements Auth {
        private String user;
        private String password;
        private boolean preemptive;
        private AuthType authType;
        
        public void basic(final String user, final String password, final boolean preemptive) {
            this.user = user;
            this.password = password;
            this.preemptive = preemptive;
            this.authType = AuthType.BASIC;
        }

        public void digest(final String user, final String password, final boolean preemptive) {
            basic(user, password, preemptive);
            this.authType = AuthType.DIGEST;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }

        public boolean getPreemptive() {
            return preemptive;
        }

        public AuthType getAuthType() {
            return authType;
        }
    }

    public static class ThreadSafeAuth implements Auth {
        volatile String user;
        volatile String password;
        volatile boolean preemptive;
        volatile AuthType authType;
        
        public ThreadSafeAuth() { }

        public ThreadSafeAuth(final BasicAuth toCopy) {
            this.user = toCopy.user;
            this.password = toCopy.password;
            this.preemptive = toCopy.preemptive;
            this.authType = toCopy.authType;
        }
        
        public void basic(final String user, final String password, final boolean preemptive) {
            this.user = user;
            this.password = password;
            this.preemptive = preemptive;
            this.authType = AuthType.BASIC;
        }

        public void digest(final String user, final String password, final boolean preemptive) {
            basic(user, password, preemptive);
            this.authType = AuthType.DIGEST;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }

        public boolean getPreemptive() {
            return preemptive;
        }

        public AuthType getAuthType() {
            return authType;
        }
    }

    public static abstract class BaseRequest implements ChainedRequest {

        final ChainedRequest parent;

        public BaseRequest(final ChainedRequest parent) {
            this.parent = parent;
        }

        public ChainedRequest getParent() {
            return parent;
        }

        public void setCharset(final String val) {
            setCharset(Charset.forName(val));
        }
        
        public void setUri(final String val) throws URISyntaxException {
            getUri().setFull(val);
        }

        public void setUri(final URI val) {
            getUri().setFull(val);
        }

        public void setUri(final URL val) throws URISyntaxException {
            getUri().setFull(val.toURI());
        }

        public Function<ChainedRequest,HttpEntity> encoder(final String contentType) {
            final Function<ChainedRequest,HttpEntity> enc =  getEncoderMap().get(contentType);
            return enc != null ? enc : null;
        }
        
        public void encoder(final String contentType, final Function<ChainedRequest,HttpEntity> val) {
            getEncoderMap().put(contentType, val);
        }
        
        public void encoder(final List<String> contentTypes, final Function<ChainedRequest,HttpEntity> val) {
            for(String contentType : contentTypes) {
                encoder(contentType, val);
            }
        }

        public void setAccept(final String[] values) {
            getHeaders().put("Accept", String.join(";", values));
        }

        public void setAccept(final List<String> values) {
            getHeaders().put("Accept", String.join(";", values));
        }

        public void setHeaders(final Map<String,String> toAdd) {
            final Map<String,String> h = getHeaders();
            for(Map.Entry<String,String> entry : toAdd.entrySet()) {
                h.put(entry.getKey(), entry.getValue());
            }
        }

        public void cookie(final String name, final String value, final Date date) {
            if(getUri() == null) {
                throw new IllegalStateException("You must set the uri before setting cookies, in the same scope, " +
                                                "so that domain and path can be properly calculated");
            }

            final URI uri = getUri().toURI();
            final BasicClientCookie cookie = new BasicClientCookie(name, value);
            cookie.setDomain(uri.getHost());
            cookie.setPath(uri.getPath());
            if(date != null) {
                cookie.setExpiryDate(date);
            }

            getCookies().add(cookie);
        }
    }

    public static class BasicRequest extends BaseRequest {
        private String contentType;
        private Charset charset;
        private UriBuilder uriBuilder;
        private final Map<String,String> headers = new LinkedHashMap<>();
        private Object body;
        private final Map<String,Function<ChainedRequest,HttpEntity>> encoderMap = new LinkedHashMap<>();
        private BasicAuth auth = new BasicAuth();
        private List<Cookie> cookies = new ArrayList(1);

        protected BasicRequest(ChainedRequest parent) {
            super(parent);
            this.uriBuilder = (parent == null) ? UriBuilder.basic(null) : UriBuilder.basic(parent.getUri());
        }

        public List<Cookie> getCookies() {
            return cookies;
        }
        
        public Map<String,Function<ChainedRequest,HttpEntity>> getEncoderMap() {
            return encoderMap;
        }
        
        public String getContentType() {
            return contentType;
        }
        
        public void setContentType(final String val) {
            this.contentType = val;
        }

        public void setCharset(final Charset val) {
            this.charset = val;
        }

        public Charset getCharset() {
            return charset;
        }
        
        public UriBuilder getUri() {
            return uriBuilder;
        }
        
        public Map<String,String> getHeaders() {
            return headers;
        }
        
        public Object getBody() {
            return body;
        }
        
        public void setBody(Object val) {
            this.body = val;
        }

        public BasicAuth getAuth() {
            return auth;
        }
    }
    
    public static class ThreadSafeRequest extends BaseRequest {
        
        private volatile String contentType;
        private volatile Charset charset;
        private volatile UriBuilder uriBuilder;
        private final ConcurrentMap<String,String> headers = new ConcurrentHashMap<>();
        private volatile Object body;
        private final ConcurrentMap<String,Function<ChainedRequest,HttpEntity>> encoderMap = new ConcurrentHashMap<>();
        private final ThreadSafeAuth auth;
        private final List<Cookie> cookies = new CopyOnWriteArrayList();

        public ThreadSafeRequest(final ChainedRequest parent) {
            super(parent);
            this.auth = new ThreadSafeAuth();
            this.uriBuilder = (parent == null) ? UriBuilder.threadSafe(null) : UriBuilder.threadSafe(parent.getUri());
        }

        public ThreadSafeRequest(final ChainedRequest parent, final BasicRequest toCopy) {
            super(parent);
            this.auth = new ThreadSafeAuth(toCopy.auth);
            this.contentType = toCopy.contentType;
            this.charset = toCopy.charset;
            this.uriBuilder = toCopy.uriBuilder;
            this.headers.putAll(toCopy.headers);
            this.body = toCopy.body;
            this.encoderMap.putAll(toCopy.encoderMap);
            this.cookies.addAll(toCopy.cookies);
        }
        
        public List<Cookie> getCookies() {
            return cookies;
        }
        
        public Map<String,Function<ChainedRequest,HttpEntity>> getEncoderMap() {
            return encoderMap;
        }
        
        public String getContentType() {
            return contentType;
        }
        
        public void setContentType(final String val) {
            this.contentType = val;
        }

        public Charset getCharset() {
            return charset;
        }
        
        public void setCharset(final Charset val) {
            this.charset = val;
        }
        
        public UriBuilder getUri() {
            return uriBuilder;
        }
        
        public Map<String,String> getHeaders() {
            return headers;
        }
        
        public Object getBody() {
            return body;
        }
        
        public void setBody(Object val) {
            this.body = val;
        }

        public ThreadSafeAuth getAuth() {
            return auth;
        }
    }

    public static abstract class BaseResponse implements ChainedResponse {

        abstract protected Map<Integer,Closure<Object>> getByCode();
        abstract protected Closure<Object> getSuccess();
        abstract protected Closure<Object> getFailure();
        abstract protected Map<String,Function<HttpResponse,Object>> getParserMap();
        
        private final ChainedResponse parent;

        public ChainedResponse getParent() {
            return parent;
        }

        protected BaseResponse(final ChainedResponse parent) {
            this.parent = parent;
        }
        
        public void when(String code, Closure<Object> closure) {
            when(Integer.valueOf(code), closure);
        }
        
        public void when(Integer code, Closure<Object> closure) {
            getByCode().put(code, closure);
        }
        
        public void when(final HttpConfig.Status status, Closure<Object> closure) {
            if(status == HttpConfig.Status.SUCCESS) {
                setSuccess(closure);
            }
            else {
                setFailure(closure);
            }
        }

        public Closure<Object> when(final Integer code) {
            if(getByCode().containsKey(code)) {
                return getByCode().get(code);
            }
            
            if(code < 399 && getSuccess() != null) {
                return getSuccess();
            }
            
            if(getFailure() != null) {
                return getFailure();
            }

            return null;
        }
        
        public Function<HttpResponse,Object> parser(final String contentType) {
            final Function<HttpResponse,Object> p = getParserMap().get(contentType);
            return p != null ? p : null;
        }
        
        public void parser(final String contentType, Function<HttpResponse,Object> val) {
            getParserMap().put(contentType, val);
        }
        
        public void parser(final List<String> contentTypes, Function<HttpResponse,Object> val) {
            for(String contentType : contentTypes) {
                parser(contentType, val);
            }
        }
    }

    public static class BasicResponse extends BaseResponse {
        private final Map<Integer,Closure<Object>> byCode = new LinkedHashMap<>();
        private Closure<Object> successHandler;
        private Closure<Object> failureHandler;
        private final Map<String,Function<HttpResponse,Object>> parserMap = new LinkedHashMap<>();

        protected BasicResponse(final ChainedResponse parent) {
            super(parent);
        }
        
        public Map<String,Function<HttpResponse,Object>> getParserMap() {
            return parserMap;
        }
        
        protected Map<Integer,Closure<Object>> getByCode() {
            return byCode;
        }

        protected Closure<Object> getSuccess() {
            return successHandler;
        }
        
        protected Closure<Object> getFailure() {
            return failureHandler;
        }

        public void setSuccess(final Closure<Object> val) {
            successHandler = val;
        }

        public void setFailure(final Closure<Object> val) {
            failureHandler = val;
        }
    }

    public static class ThreadSafeResponse extends BaseResponse {
        private final ConcurrentMap<String,Function<HttpResponse,Object>> parserMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<Integer,Closure<Object>> byCode = new ConcurrentHashMap<>();
        private volatile Closure<Object> successHandler;
        private volatile Closure<Object> failureHandler;

        public ThreadSafeResponse(final ChainedResponse parent) {
            super(parent);
        }

        public ThreadSafeResponse(final ChainedResponse response, final BasicResponse toCopy) {
            super(response);
            this.parserMap.putAll(toCopy.parserMap);
            this.byCode.putAll(toCopy.byCode);
            this.successHandler = toCopy.successHandler;
            this.failureHandler = toCopy.failureHandler;
        }
        
        protected Map<String,Function<HttpResponse,Object>> getParserMap() {
            return parserMap;
        }
        
        protected Map<Integer,Closure<Object>> getByCode() {
            return byCode;
        }

        protected Closure<Object> getSuccess() {
            return successHandler;
        }
        
        protected Closure<Object> getFailure() {
            return failureHandler;
        }

        public void setSuccess(final Closure<Object> val) {
            successHandler = val;
        }

        public void setFailure(final Closure<Object> val) {
            failureHandler = val;
        }
    }

    public abstract static class BaseHttpConfig implements ChainedHttpConfig {

        private final ChainedHttpConfig parent;

        public BaseHttpConfig(ChainedHttpConfig parent) {
            this.parent = parent;
        }

        public ChainedHttpConfig getParent() {
            return parent;
        }
        
        public ChainedHttpConfig configure(final String scriptClassPath) {
            final CompilerConfiguration compilerConfig = new CompilerConfiguration();
            final ImportCustomizer icustom = new ImportCustomizer();
            icustom.addImports("groovyx.net.http.NativeHandlers");
            icustom.addStaticStars("groovyx.net.http.Status", "groovyx.net.http.ContentTypes");
            final Map<String,String> map = Collections.singletonMap("extensions", TYPE_CHECKING_SCRIPT);
            final ASTTransformationCustomizer ast = new ASTTransformationCustomizer(map, TypeChecked.class);
            compilerConfig.addCompilationCustomizers(icustom, ast);
            final GroovyShell shell = new GroovyShell(getClass().getClassLoader(), compilerConfig);
            shell.setVariable("request", getRequest());
            shell.setVariable("response", getResponse());
            
            try(final InputStream is = getClass().getClassLoader().getResourceAsStream(scriptClassPath)) {
                final InputStreamReader reader = new InputStreamReader(is);
                shell.evaluate(reader);
                return this;
            }
            catch(IOException ioe) {
                throw new RuntimeException();
            }
        }

        public ChainedHttpConfig config(@DelegatesTo(HttpConfig.class) final Closure closure) {
            closure.setDelegate(this);
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.call();
            return this;
        }
    }
    
    public static class ThreadSafeHttpConfig extends BaseHttpConfig {
        final ThreadSafeRequest request;
        final ThreadSafeResponse response;
        
        public ThreadSafeHttpConfig(final ChainedHttpConfig parent) {
            super(parent);
            if(parent == null) {
                this.request = new ThreadSafeRequest(null);
                this.response = new ThreadSafeResponse(null);
            }
            else {
                this.request = new ThreadSafeRequest(parent.getChainedRequest());
                this.response = new ThreadSafeResponse(parent.getChainedResponse());
            }
        }

        public Request getRequest() {
            return request;
        }

        public Response getResponse() {
            return response;
        }

        public ChainedRequest getChainedRequest() {
            return request;
        }

        public ChainedResponse getChainedResponse() {
            return response;
        }
    }

    public static class BasicHttpConfig extends BaseHttpConfig {
        final BasicRequest request;
        final BasicResponse response;

        public BasicHttpConfig(final ChainedHttpConfig parent) {
            super(parent);
            if(parent == null) {
                this.request = new BasicRequest(null);
                this.response = new BasicResponse(null);
            }
            else {
                this.request = new BasicRequest(parent.getChainedRequest());
                this.response = new BasicResponse(parent.getChainedResponse());
            }
        }

        public Request getRequest() {
            return request;
        }

        public Response getResponse() {
            return response;
        }

        public ChainedRequest getChainedRequest() {
            return request;
        }

        public ChainedResponse getChainedResponse() {
            return response;
        }
    }

    private static final String CONFIG = "59f7b2e5d5a78b25c6b21eb3b6b4f9ff77d11671.groovy";
    private static final ThreadSafeHttpConfig root = (ThreadSafeHttpConfig) new ThreadSafeHttpConfig(null).configure(CONFIG);

    public static ChainedHttpConfig root() {
        return root;
    }

    public static ChainedHttpConfig threadSafe(final ChainedHttpConfig parent) {
        return new ThreadSafeHttpConfig(parent);
    }

    public static ChainedHttpConfig classLevel(final boolean threadSafe) {
        return threadSafe ? threadSafe(root) : basic(root);
    }

    public static ChainedHttpConfig basic(final ChainedHttpConfig parent) {
        return new BasicHttpConfig(parent);
    }

    public static ChainedHttpConfig requestLevel(final ChainedHttpConfig parent) {
        return new BasicHttpConfig(parent);
    }

    private static final String TYPE_CHECKING_SCRIPT = "typecheckhttpconfig.groovy";
}

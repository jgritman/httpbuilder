package groovyx.net.http;

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

public abstract class AbstractHttpConfig implements HttpConfig {

    abstract public AbstractHttpConfig getParent();

    public class BasicAuth implements Auth, EffectiveAuth {
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

    public class ThreadSafeAuth implements Auth, EffectiveAuth {
        volatile String user;
        volatile String password;
        volatile boolean preemptive;
        volatile AuthType authType;
        
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

    public abstract class BaseRequest implements Request {

        protected abstract String getContentType();
        protected abstract Object getBody();
        protected abstract Charset getCharset();
        protected abstract Map<String,Function<EffectiveRequest,HttpEntity>> getEncoderMap();
        protected abstract EffectiveAuth getEffectiveAuth();
        
        private final Effective effective = new Effective();
        
        public void setCharset(final String val) {
            setCharset(Charset.forName(val));
        }

        public Function<EffectiveRequest,HttpEntity> encoder(final String contentType) {
            final Function<EffectiveRequest,HttpEntity> enc =  getEncoderMap().get(contentType);
            return enc != null ? enc : null;
        }
        
        public void encoder(final String contentType, final Function<EffectiveRequest,HttpEntity> val) {
            getEncoderMap().put(contentType, val);
        }
        
        public void encoder(final List<String> contentTypes, final Function<EffectiveRequest,HttpEntity> val) {
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

        public EffectiveRequest getEffective() {
            return effective;
        }

        public class Effective implements EffectiveRequest {

            public Charset charset() {
                final Charset val = getCharset();
                if(val != null) {
                    return val;
                }
                
                if(getParent() != null) {
                    return getParent().getRequest().getEffective().charset();
                }
                
                return null;
            }
        
            public String contentType() {
                if(getContentType() != null) {
                    return getContentType();
                }
                
                if(getParent() != null) {
                    return getParent().getRequest().getEffective().contentType();
                }
                
                return null;
            }

            public Object body() {
                if(getBody() != null) {
                    return getBody();
                }
                
                if(getParent() != null) {
                    return getParent().getRequest().getEffective().body();
                }
                
                return null;
            }

            public URIBuilder uri() {
                if(getUri() != null) {
                    return getUri();
                }
                
                if(getParent() != null) {
                    return getParent().getRequest().getEffective().uri();
                }
                
                return null;
            }

            public Map<String,String> headers(final Map<String,String> map) {
                if(getParent() != null) {
                    getParent().getRequest().getEffective().headers(map);
                }
                
                map.putAll(getHeaders());
                return map;
            }

            public Function<EffectiveRequest,HttpEntity> encoder(final String contentType) {
                final Function<EffectiveRequest,HttpEntity> e = getEncoderMap().get(contentType);
                if(e != null) {
                    return e;
                }
                
                if(getParent() != null) {
                    return getParent().getRequest().getEffective().encoder(contentType);
                }
                
                return null;
            }

            public EffectiveAuth auth() {
                EffectiveAuth ea = getEffectiveAuth();
                if(ea != null) {
                    return ea;
                }

                if(getParent() != null) {
                    return getParent().getRequest().getEffective().auth();
                }

                return null;
            }
        }
    }

    public class BasicRequest extends BaseRequest {
        private String contentType;
        private Charset charset;
        private URIBuilder uriBuilder;
        private final Map<String,String> headers = new LinkedHashMap<>();
        private Object body;
        private final Map<String,Function<EffectiveRequest,HttpEntity>> encoderMap = new LinkedHashMap<>();
        private BasicAuth auth = new BasicAuth();
        
        protected Map<String,Function<EffectiveRequest,HttpEntity>> getEncoderMap() {
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
        
        public URIBuilder getUri() {
            return uriBuilder;
        }
        
        public void setUri(final URIBuilder val) {
            this.uriBuilder = val;
        }

        public void setUri(final String val) throws URISyntaxException {
            this.uriBuilder = new URIBuilder(val);
        }

        public void setUri(final URI val) {
            this.uriBuilder = new URIBuilder(val);
        }

        public void setUri(final URL val) throws URISyntaxException {
            this.uriBuilder = new URIBuilder(val);
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

        public EffectiveAuth getEffectiveAuth() {
            return auth.getAuthType() != null ? auth : null;
        }
    }
    
    public class ThreadSafeRequest extends BaseRequest {
        
        private volatile String contentType;
        private volatile Charset charset;
        private volatile URIBuilder uriBuilder;
        private final ConcurrentMap<String,String> headers = new ConcurrentHashMap<>();
        private volatile Object body;
        private final ConcurrentMap<String,Function<EffectiveRequest,HttpEntity>> encoderMap = new ConcurrentHashMap<>();
        private final ThreadSafeAuth auth = new ThreadSafeAuth();
        
        protected Map<String,Function<EffectiveRequest,HttpEntity>> getEncoderMap() {
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
        
        public URIBuilder getUri() {
            return uriBuilder;
        }
        
        public void setUri(final URIBuilder val) {
            this.uriBuilder = val;
        }

        public void setUri(final String val) throws URISyntaxException {
            this.uriBuilder = new URIBuilder(val);
        }

        public void setUri(final URI val) {
            this.uriBuilder = new URIBuilder(val);
        }

        public void setUri(final URL val) throws URISyntaxException {
            this.uriBuilder = new URIBuilder(val);
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

        public EffectiveAuth getEffectiveAuth() {
            return auth.getAuthType() != null ? auth : null;
        }
    }

    public abstract class BaseResponse implements Response {

        abstract protected Map<Integer,Closure<Object>> getByCode();
        abstract protected Closure<Object> getSuccess();
        abstract protected Closure<Object> getFailure();
        abstract protected Map<String,Function<HttpResponse,Object>> getParserMap();

        private final Effective effective = new Effective();

        public EffectiveResponse getEffective() {
            return effective;
        }
        
        public void when(String code, Closure<Object> closure) {
            when(Integer.valueOf(code), closure);
        }
        
        public void when(Integer code, Closure<Object> closure) {
            getByCode().put(code, closure);
        }
        
        public void when(Status status, Closure<Object> closure) {
            if(status == Status.SUCCESS) {
                setSuccess(closure);
            }
            else {
                setFailure(closure);
            }
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

        public class Effective implements EffectiveResponse {
            
            public Closure<Object> action(final Integer code) {
                Closure<Object> ret = getByCode().get(code);
                if(ret != null) {
                    return ret;
                }
                
                if(code < 399 && getSuccess() != null) {
                    return getSuccess();
                }
                
                if(getFailure() != null) {
                    return getFailure();
                }
                
                if(getParent() != null) {
                    return getParent().getResponse().getEffective().action(code);
                }
                
                return null;
            }
            
            public Function<HttpResponse,Object> parser(final String contentType) {
                Function<HttpResponse,Object> p = BaseResponse.this.parser(contentType);
                if(p != null) {
                    return p;
                }

                if(getParent() != null) {
                    return getParent().getResponse().getEffective().parser(contentType);
                }

                return null;
            }
        }
    }

    public class BasicResponse extends BaseResponse {
        private final Map<Integer,Closure<Object>> byCode = new LinkedHashMap<>();
        private Closure<Object> successHandler;
        private Closure<Object> failureHandler;
        private final Map<String,Function<HttpResponse,Object>> parserMap = new LinkedHashMap<>();

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

    public class ThreadSafeResponse extends BaseResponse {
        private final ConcurrentMap<String,Function<HttpResponse,Object>> parserMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<Integer,Closure<Object>> byCode = new ConcurrentHashMap<>();
        private volatile Closure<Object> successHandler;
        private volatile Closure<Object> failureHandler;

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

    public HttpConfig config(@DelegatesTo(HttpConfig.class) final Closure closure) {
        closure.setDelegate(this);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();

        return this;
    }

    public static class ThreadSafeHttpConfig extends AbstractHttpConfig {
        final AbstractHttpConfig parent;
        final ThreadSafeRequest request;
        final ThreadSafeResponse response;

        public ThreadSafeHttpConfig(final AbstractHttpConfig parent) {
            this.parent = parent;
            this.request = new ThreadSafeRequest();
            this.response = new ThreadSafeResponse();
            if(parent != null && parent.getRequest().getUri() != null) {
                this.request.setUri(parent.getRequest().getUri());
            }
        }

        public Request getRequest() {
            return request;
        }

        public Response getResponse() {
            return response;
        }

        public AbstractHttpConfig getParent() {
            return parent;
        }
    }

    public static class BasicHttpConfig extends AbstractHttpConfig {
        final AbstractHttpConfig parent;
        final BasicRequest request;
        final BasicResponse response;

        public BasicHttpConfig(final AbstractHttpConfig parent) {
            this.parent = parent;
            this.request = new BasicRequest();
            this.response = new BasicResponse();
            if(parent != null && parent.getRequest().getUri() != null) {
                this.request.setUri(parent.getRequest().getUri());
            }
        }

        public Request getRequest() {
            return request;
        }

        public Response getResponse() {
            return response;
        }

        public AbstractHttpConfig getParent() {
            return parent;
        }
    }

    private static final String CONFIG = "59f7b2e5d5a78b25c6b21eb3b6b4f9ff77d11671.groovy";
    private static final ThreadSafeHttpConfig root = (ThreadSafeHttpConfig) threadSafe(null).configure(CONFIG);

    public static HttpConfig root() {
        return root;
    }

    public static AbstractHttpConfig threadSafe(final HttpConfig parent) {
        return new ThreadSafeHttpConfig((AbstractHttpConfig) parent);
    }

    public static AbstractHttpConfig classLevel(final boolean threadSafe) {
        return threadSafe ? threadSafe(root) : basic(root);
    }

    public static AbstractHttpConfig basic(final HttpConfig parent) {
        return new BasicHttpConfig((AbstractHttpConfig) parent);
    }

    public static AbstractHttpConfig requestLevel(final HttpConfig parent) {
        return new BasicHttpConfig((AbstractHttpConfig) parent);
    }

    private static final String TYPE_CHECKING_SCRIPT = "typecheckhttpconfig.groovy";
    
    public AbstractHttpConfig configure(final String scriptClassPath) {
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
}

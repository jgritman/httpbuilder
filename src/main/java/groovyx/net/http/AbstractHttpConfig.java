package groovyx.net.http;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

public abstract class AbstractHttpConfig implements HttpConfig, Effective {
    
    public static class ImmutableContentHandler implements ContentHandler {

        private final Function<Effective.Req,HttpEntity> encoder;
        private final Function<HttpResponse,Object> parser;
        
        public ImmutableContentHandler(final Function<Effective.Req,HttpEntity> encoder,
                                       final Function<HttpResponse,Object> parser) {
            this.encoder = encoder;
            this.parser = parser;
        }

        public ImmutableContentHandler encoder(final Function<Effective.Req,HttpEntity> encoder) {
            return new ImmutableContentHandler(encoder, parser);
        }

        public ImmutableContentHandler parser(final Function<HttpResponse,Object> parser) {
            return new ImmutableContentHandler(encoder, parser);
        }

        public Function<Effective.Req,HttpEntity> getEncoder() {
            return encoder;
        }
        
        public Function<HttpResponse,Object> getParser() {
            return parser;
        }
    }

    public abstract class BaseRequest implements Request, Effective.Req {

        protected abstract String getContentType();
        protected abstract Object getBody();
        protected abstract Charset getCharset();

        public void setCharset(final String val) {
            setCharset(Charset.forName(val));
        }

        public Charset effectiveCharset() {
            final Charset val = getCharset();
            if(val != null) {
                return val;
            }

            if(getParent() != null) {
                return ((BaseRequest) getParent().getRequest()).effectiveCharset();
            }

            return null;
        }
        
        public String effectiveContentType() {
            if(getContentType() != null) {
                return getContentType();
            }

            if(getParent() != null) {
                return ((BaseRequest) getParent().getRequest()).effectiveContentType();
            }

            return null;
        }

        public Object effectiveBody() {
            if(getBody() != null) {
                return getBody();
            }

            if(getParent() != null) {
                return ((BaseRequest) getParent().getRequest()).effectiveBody();
            }

            return null;
        }

        public URIBuilder effectiveUri() {
            if(getUri() != null) {
                return getUri();
            }

            if(getParent() != null) {
                return ((BaseRequest) getParent().getRequest()).effectiveUri();
            }

            return null;
        }

        public Map<String,String> effectiveHeaders(final Map<String,String> map) {
            if(getParent() != null) {
                ((BaseRequest) getParent()).effectiveHeaders(map);
            }

            map.putAll(getHeaders());
            return map;
        }
    }

    public class BasicRequest extends BaseRequest {
        private String contentType;
        private Charset charset;
        private URIBuilder uriBuilder;
        private Map<String,String> headers = new LinkedHashMap<>();
        private Object body;
        
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
    }
    
    public class ThreadSafeRequest extends BaseRequest {
        
        private volatile String contentType;
        private volatile Charset charset;
        private volatile URIBuilder uriBuilder;
        private final ConcurrentMap<String,String> headers = new ConcurrentHashMap<>();
        private volatile Object body;
        
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
    }

    public abstract class BaseResponse implements Response, Effective.Resp {

        abstract protected Map<Integer,Closure<Object>> getByCode();
        abstract protected Closure<Object> getSuccess();
        abstract protected Closure<Object> getFailure();

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

        public Closure<Object> effectiveAction(final Integer code) {
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
                return ((BaseResponse) getParent().getResponse()).effectiveAction(code);
            }

            return null;
        }
    }

    public class BasicResponse extends BaseResponse {
        private Map<Integer,Closure<Object>> byCode = new LinkedHashMap<>();
        private Closure<Object> successHandler;
        private Closure<Object> failureHandler;

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

        private ConcurrentMap<Integer,Closure<Object>> byCode = new ConcurrentHashMap<>();
        private volatile Closure<Object> successHandler;
        private volatile Closure<Object> failureHandler;

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

    abstract public HttpConfig getParent();

    abstract protected Map<String,ContentHandler> getContentHandlers();

    public Function<Effective.Req,HttpEntity> encoder(final String contentType) {
        final ImmutableContentHandler handler = (ImmutableContentHandler) getContentHandlers().get(contentType);
        return handler == null ? null : handler.getEncoder();
    }

    public Function<HttpResponse,Object> parser(final String contentType) {
        final ImmutableContentHandler handler = (ImmutableContentHandler) getContentHandlers().get(contentType);
        return handler == null ? null : handler.getParser();
    }

    public void encoder(final String contentType, final Function<Effective.Req,HttpEntity> val) {
        final ImmutableContentHandler before = (ImmutableContentHandler) getContentHandlers().get(contentType);
        if(before != null) {
            getContentHandlers().put(contentType, before.encoder(val));
        }
        else {
            getContentHandlers().put(contentType, new ImmutableContentHandler(val, null));
        }
    }
    
    public void encoder(final List<String> contentTypes, final Function<Effective.Req,HttpEntity> val) {
        for(String contentType : contentTypes) {
            encoder(contentType, val);
        }
    }

    public void parser(final String contentType, Function<HttpResponse,Object> val) {
        final ImmutableContentHandler before = (ImmutableContentHandler) getContentHandlers().get(contentType);
        if(before != null) {
            getContentHandlers().put(contentType, before.parser(val));
        }
        else {
            getContentHandlers().put(contentType, new ImmutableContentHandler(null, val));
        }
    }
    
    public void parser(final List<String> contentTypes, Function<HttpResponse,Object> val) {
        for(String contentType : contentTypes) {
            parser(contentType, val);
        }
    }

    private Map<String,ContentHandler> parserContentHandlers(final String contentType) {
        ContentHandler ret = getContentHandlers().get(contentType);
        if(ret != null && ret.getParser() != null) {
            return getContentHandlers();
        }
        
        if(getParent() != null) {
            return ((AbstractHttpConfig) getParent()).parserContentHandlers(contentType);
        }

        return null;
    }
    
    public Function<HttpResponse,Object> effectiveParser(final String contentType) {
        Map<String,ContentHandler> map = parserContentHandlers(contentType);
        return map == null ? null : map.get(contentType).getParser();
    }

    public Set<String> acceptHeader(final String contentType) {
        Map<String,ContentHandler> map = parserContentHandlers(contentType);
        if(map == null) {
            return Collections.emptySet();
        }

        final Set<String> ret = new HashSet<>();
        final Function<HttpResponse,Object> parser = map.get(contentType).getParser();
        for(Map.Entry<String,ContentHandler> entry : map.entrySet()) {
            if(entry.getValue().getParser() == parser) {
                ret.add(entry.getKey());
            }
        }

        return ret;
    }

    public Function<Effective.Req,HttpEntity> effectiveEncoder(final String contentType) {
        ContentHandler ret = getContentHandlers().get(contentType);
        if(ret != null && ret.getEncoder() != null) {
            return ret.getEncoder();
        }

        if(getParent() != null) {
            return ((AbstractHttpConfig) getParent()).effectiveEncoder(contentType);
        }

        return null;
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
        final ConcurrentMap<String,ContentHandler> contentHandlers = new ConcurrentHashMap<>();

        public ThreadSafeHttpConfig(final AbstractHttpConfig parent) {
            this.parent = parent;
            this.request = new ThreadSafeRequest();
            this.response = new ThreadSafeResponse();
        }

        protected Map<String,ContentHandler> getContentHandlers() {
            return contentHandlers;
        }

        public Request getRequest() {
            return request;
        }

        public Req getReq() {
            return request;
        }

        public Response getResponse() {
            return response;
        }

        public Resp getResp() {
            return response;
        }

        public HttpConfig getParent() {
            return parent;
        }
    }

    public static class BasicHttpConfig extends AbstractHttpConfig {
        final AbstractHttpConfig parent;
        final BasicRequest request;
        final BasicResponse response;
        final Map<String,ContentHandler> contentHandlers = new LinkedHashMap<>();

        public BasicHttpConfig(final AbstractHttpConfig parent) {
            this.parent = parent;
            this.request = new BasicRequest();
            this.response = new BasicResponse();
        }

        protected Map<String,ContentHandler> getContentHandlers() {
            return contentHandlers;
        }

        public Request getRequest() {
            return request;
        }

        public Req getReq() {
            return request;
        }

        public Response getResponse() {
            return response;
        }

        public Resp getResp() {
            return response;
        }

        public HttpConfig getParent() {
            return parent;
        }
    }

    private static final ThreadSafeHttpConfig root = (ThreadSafeHttpConfig) threadSafe(null);

    public static AbstractHttpConfig root() {
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
}

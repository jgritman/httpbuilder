package groovyx.net.http;

import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.Map;
import java.util.LinkedHashMap;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import groovy.lang.Closure;
import java.util.Collections;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

public abstract class AbstractHttpConfig implements HttpConfig {
    
    public class ImmutableContentHandler implements ContentHandler {

        private final Function<Effective.Request,HttpEntity> encoder;
        private final Function<HttpResponse,Object> parser;
        
        public ImmutableContentHandler(final Function<Effective.Request,HttpEntity> encoder,
                                       final Function<HttpResponse,Object> parser) {
            this.encoder = encoder;
            this.parser = parser;
        }

        public ImmutableContentHandler encoder(final Function<Effective.Request,HttpEntity> encoder) {
            return new ImmutableContentHandler(encoder, parser);
        }

        public ImmutableContentHandler parser(final Function<HttpResponse,Object> parser) {
            return new ImmutableContentHandler(encoder, parser);
        }

        public Function<Effective.Request,HttpEntity> getEncoder() {
            return encoder;
        }
        
        public Function<HttpResponse,Object> getParser() {
            return parser;
        }
    }

    public abstract class BaseRequest implements Request, Effective.Request {

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
                return ((BaseRequest) getParent()).effectiveCharset();
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

        public void setUri(final String val) {
            try {
                this.uriBuilder = new URIBuilder(val);
            }
            catch(URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        }

        public void setUri(final URI val) {
            this.uriBuilder = new URIBuilder(val);
        }

        public void setUri(final URL val) {
            try {
                this.uriBuilder = new URIBuilder(val);
            }
            catch(URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
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
        private URIBuilder uriBuilder;
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
            synchronized(uriBuilder) {
                return uriBuilder;
            }
        }
        
        public void setUri(final URIBuilder val) {
            synchronized(uriBuilder) {
                this.uriBuilder = val;
            }
        }

        public void setUri(final String val) {
            synchronized(uriBuilder) {
                try {
                    this.uriBuilder = new URIBuilder(val);
                }
                catch(URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        public void setUri(final URI val) {
            synchronized(uriBuilder) {
                this.uriBuilder = new URIBuilder(val);
            }
        }

        public void setUri(final URL val) {
            synchronized(uriBuilder) {
                try {
                    this.uriBuilder = new URIBuilder(val);
                }
                catch(URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
            }
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

    public abstract class BaseResponse implements Response, Effective.Response {

        abstract protected Map<Integer,Closure<Object>> getByCode();
        abstract protected Closure<Object> getSuccess();
        abstract protected Closure<Object> getFailure();

        public void when(String code, Closure<Object> closure) {
            when(Integer.valueOf(code), closure);
        }
        
        public void setSuccess(Closure<Object> closure) {
            when(Status.FAILURE, closure);
        }
        
        public void setFailure(Closure<Object> closure) {
            when(Status.SUCCESS, closure);
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
    }

    abstract public HttpConfig getParent();

    abstract protected Map<String,ContentHandler> getContentHandlers();

    public Function<Effective.Request,HttpEntity> encoder(final String contentType) {
        final ImmutableContentHandler handler = (ImmutableContentHandler) getContentHandlers().get(contentType);
        return handler == null ? null : handler.getEncoder();
    }

    public Function<HttpResponse,Object> parser(final String contentType) {
        final ImmutableContentHandler handler = (ImmutableContentHandler) getContentHandlers().get(contentType);
        return handler == null ? null : handler.getParser();
    }
    
    public void encoder(String[] contentTypes, Function<Effective.Request,HttpEntity> val) {
        for(String contentType : contentTypes) {
            final ImmutableContentHandler before = (ImmutableContentHandler) getContentHandlers().get(contentType);
            if(before != null) {
                getContentHandlers().put(contentType, before.encoder(val));
            }
            else {
                getContentHandlers().put(contentType, new ImmutableContentHandler(val, null));
            }
        }
    }
    
    public void parser(String[] contentTypes, Function<HttpResponse,Object> val) {
        for(String contentType : contentTypes) {
            final ImmutableContentHandler before = (ImmutableContentHandler) getContentHandlers().get(contentType);
            if(before != null) {
                getContentHandlers().put(contentType, before.parser(val));
            }
            else {
                getContentHandlers().put(contentType, new ImmutableContentHandler(null, val));
            }
        }
    }
    
    public Function<HttpResponse,Object> effectiveParser(final String contentType) {
        ContentHandler ret = getContentHandlers().get(contentType);
        if(ret != null && ret.getParser() != null) {
            return ret.getParser();
        }

        if(getParent() != null) {
            return ((AbstractHttpConfig) getParent()).effectiveParser(contentType);
        }

        return null;
    }

    public Function<Effective.Request,HttpEntity> effectiveEncoder(final String contentType) {
        ContentHandler ret = getContentHandlers().get(contentType);
        if(ret != null && ret.getEncoder() != null) {
            return ret.getEncoder();
        }

        if(getParent() != null) {
            return ((AbstractHttpConfig) getParent()).effectiveEncoder(contentType);
        }

        return null;
    }

    public class ThreadSafeHttpConfig extends AbstractHttpConfig {
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

        public Response getResponse() {
            return response;
        }

        public HttpConfig getParent() {
            return parent;
        }
    }
}

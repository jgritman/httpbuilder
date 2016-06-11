package groovyx.net.http;

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

        private final Function<Object,HttpEntity> encoder;
        private final Function<HttpResponse,Object> parser;
        
        public ImmutableContentHandler(final Function<Object,HttpEntity> encoder,
                                       final Function<HttpResponse,Object> parser) {
            this.encoder = encoder;
            this.parser = parser;
        }

        public ImmutableContentHandler encoder(final Function<Object,HttpEntity> encoder) {
            return new ImmutableContentHandler(encoder, parser);
        }

        public ImmutableContentHandler parser(final Function<HttpResponse,Object> parser) {
            return new ImmutableContentHandler(encoder, parser);
        }

        public Function<Object,HttpEntity> getEncoder() {
            return encoder;
        }
        
        public Function<HttpResponse,Object> getParser() {
            return parser;
        }
    }

    public abstract class BaseRequestConfig implements Request {

        protected abstract String getContentType();
        protected abstract Object getBody();
        
        public String effectiveContentType() {
            if(getContentType() != null) {
                return getContentType();
            }

            if(getParent() != null) {
                return ((BaseRequestConfig) getParent().getRequest()).effectiveContentType();
            }

            return null;
        }

        public Object effectiveBody() {
            if(getBody() != null) {
                return getBody();
            }

            if(getParent() != null) {
                return ((BaseRequestConfig) getParent().getRequest()).effectiveBody();
            }

            return null;
        }

        public URIBuilder effectiveUri() {
            if(getUri() != null) {
                return getUri();
            }

            if(getParent() != null) {
                return ((BaseRequestConfig) getParent().getRequest()).effectiveUri();
            }

            return null;
        }

        public Map<String,String> effectiveHeaders(final Map<String,String> map) {
            if(getParent() != null) {
                ((BaseRequestConfig) getParent()).effectiveHeaders(map);
            }

            map.putAll(getHeaders());
            return map;
        }
    }

    public class BasicRequestConfig extends BaseRequestConfig {
        private String contentType;
        private URIBuilder uriBuilder;
        private Map<String,String> headers = new LinkedHashMap<>();
        private Object body;
        
        public String getContentType() {
            return contentType;
        }
        
        public void setContentType(final String val) {
            this.contentType = val;
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
    
    public class ThreadSafeRequestConfig extends BaseRequestConfig {
        
        private volatile String contentType;
        private URIBuilder uriBuilder;
        private final ConcurrentMap<String,String> headers = new ConcurrentHashMap<>();
        private volatile Object body;
        
        public String getContentType() {
            return contentType;
        }
        
        public void setContentType(final String val) {
            this.contentType = val;
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

    public abstract class BaseResponse implements Response {

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

        protected Closure<Object> effectiveAction(final Integer code) {
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
                BaseResponse baseResponse = (BaseResponse) getParent().getResponse();
                return baseResponse.effectiveAction(code);
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

    public void encoder(String contentType, Function<Object,HttpEntity> val) {
        final ImmutableContentHandler before = (ImmutableContentHandler) getContentHandlers().get(contentType);
        if(before != null) {
            getContentHandlers().put(contentType, before.encoder(val));
        }
        else {
            getContentHandlers().put(contentType, new ImmutableContentHandler(val, null));
        }
    }
    
    public void parser(String contentType, Function<HttpResponse,Object> val) {
        final ImmutableContentHandler before = (ImmutableContentHandler) getContentHandlers().get(contentType);
        if(before != null) {
            getContentHandlers().put(contentType, before.parser(val));
        }
        else {
            getContentHandlers().put(contentType, new ImmutableContentHandler(null, val));
        }
    }

    protected Function<HttpResponse,Object> effectiveParser(final String contentType) {
        ContentHandler ret = getContentHandlers().get(contentType);
        if(ret != null && ret.getParser() != null) {
            return ret.getParser();
        }

        if(getParent() != null) {
            return ((AbstractHttpConfig) getParent()).effectiveParser(contentType);
        }

        return null;
    }

    protected Function<Object,HttpEntity> effectiveEncoder(final String contentType) {
        ContentHandler ret = getContentHandlers().get(contentType);
        if(ret != null && ret.getEncoder() != null) {
            return ret.getEncoder();
        }

        if(getParent() != null) {
            return ((AbstractHttpConfig) getParent()).effectiveEncoder(contentType);
        }

        return null;
    }
}

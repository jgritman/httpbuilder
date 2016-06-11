package groovyx.net.http;

import java.util.function.Function;
import java.util.Map;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import groovy.lang.Closure;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import java.nio.charset.Charset;
import java.util.function.Function;

public interface HttpConfig {

    public enum Status { SUCCESS, FAILURE };

    public interface ContentHandler {
        Function<Effective.Request,HttpEntity> getEncoder();
        Function<HttpResponse,Object> getParser();
    }

    public interface Request {

        void setContentType(String val);
        void setCharset(String val);
        void setCharset(Charset val);
        
        URIBuilder getUri();
        void setUri(URIBuilder val);
        void setUri(String val);
        void setUri(URI val);
        void setUri(URL val);

        Map<String,String> getHeaders();

        void setBody(Object val);
    }

    public interface Response {
        void when(Status status, Closure<Object> closure);
        void when(Integer code, Closure<Object> closure);
        void when(String code, Closure<Object> closure);

        void setSuccess(Closure<Object> closure);
        void setFailure(Closure<Object> closure);
    }

    void encoder(String[] contentTypes, Function<Effective.Request,HttpEntity> val);
    Function<Effective.Request,HttpEntity> encoder(String contentType);
    void parser(String[] contentTypes, Function<HttpResponse,Object> val);
    Function<HttpResponse,Object> parser(String contentType);
    
    Request getRequest();
    Response getResponse();
}

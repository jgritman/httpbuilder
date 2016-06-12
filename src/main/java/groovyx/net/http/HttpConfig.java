package groovyx.net.http;

import groovy.lang.Closure;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;

public interface HttpConfig {

    public enum Status { SUCCESS, FAILURE };

    public interface ContentHandler {
        Function<Effective.Req,HttpEntity> getEncoder();
        Function<HttpResponse,Object> getParser();
    }

    public interface Request {

        void setContentType(String val);
        void setCharset(String val);
        void setCharset(Charset val);
        
        URIBuilder getUri();
        void setUri(URIBuilder val);
        void setUri(String val) throws URISyntaxException;
        void setUri(URI val);
        void setUri(URL val) throws URISyntaxException;

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

    void encoder(String contentType, Function<Effective.Req,HttpEntity> val);
    void encoder(List<String> contentTypes, Function<Effective.Req,HttpEntity> val);
    Function<Effective.Req,HttpEntity> encoder(String contentType);

    void parser(String contentType, Function<HttpResponse,Object> val);
    void parser(List<String> contentTypes, Function<HttpResponse,Object> val);
    Function<HttpResponse,Object> parser(String contentType);
    
    Request getRequest();
    Response getResponse();
}

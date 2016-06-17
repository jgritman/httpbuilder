package groovyx.net.http;

import groovy.lang.Closure;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;

public interface HttpConfig {

    public enum Status { SUCCESS, FAILURE };

    public interface EffectiveRequest {
        Charset charset();
        String contentType();
        Object body();
        URIBuilder uri();
        Map<String,String> headers(Map<String,String> val);
        Function<EffectiveRequest,HttpEntity> encoder(String contentType);
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
        void setHeaders(Map<String,String> toAdd);

        void setAccept(String[] values);
        void setAccept(List<String> values);
        void setBody(Object val);

        void encoder(String contentType, Function<EffectiveRequest,HttpEntity> val);
        void encoder(List<String> contentTypes, Function<EffectiveRequest,HttpEntity> val);
        Function<EffectiveRequest,HttpEntity> encoder(String contentType);

        EffectiveRequest getEffective();
    }

    public interface EffectiveResponse {
        Closure<Object> action(Integer code);
        Function<HttpResponse,Object> parser(String contentType);
    }
    
    public interface Response {
        void when(Status status, Closure<Object> closure);
        void when(Integer code, Closure<Object> closure);
        void when(String code, Closure<Object> closure);

        void setSuccess(Closure<Object> closure);
        void setFailure(Closure<Object> closure);

        void parser(String contentType, Function<HttpResponse,Object> val);
        void parser(List<String> contentTypes, Function<HttpResponse,Object> val);
        Function<HttpResponse,Object> parser(String contentType);

        EffectiveResponse getEffective();
    }

    Request getRequest();
    Response getResponse();
}

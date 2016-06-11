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

public interface HttpConfig {

    public enum Status { SUCCESS, FAILURE };

    public interface ContentHandler {
        Function<Object,HttpEntity> getEncoder();
        Function<HttpResponse,Object> getParser();
    }

    public interface Request {

        void setContentType(String val);

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

    void encoder(String[] contentType, Function<Object,HttpEntity> val);
    void parser(String[] contentType, Function<HttpResponse,Object> val);
    Request getRequest();
    Response getResponse();
}

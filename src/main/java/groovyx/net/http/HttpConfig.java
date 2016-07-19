package groovyx.net.http;

import groovy.lang.Closure;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

public interface HttpConfig {

    public enum Status { SUCCESS, FAILURE };
    public enum AuthType { BASIC, DIGEST };

    public interface Auth {
        AuthType getAuthType();
        String getUser();
        String getPassword();
        boolean getPreemptive();
        
        default void basic(String user, String password) {
            basic(user, password, false);
        }
        
        void basic(String user, String password, boolean preemptive);

        default void digest(String user, String password) {
            digest(user, password, false);
        }
        
        void digest(String user, String password, boolean preemptive);
    }

    public interface Request {
        Auth getAuth();
        void setContentType(String val);
        void setCharset(String val);
        void setCharset(Charset val);
        
        UriBuilder getUri();
        void setUri(String val) throws URISyntaxException;
        void setUri(URI val);
        void setUri(URL val) throws URISyntaxException;

        Map<String,String> getHeaders();
        void setHeaders(Map<String,String> toAdd);

        void setAccept(String[] values);
        void setAccept(List<String> values);
        void setBody(Object val);

        default void cookie(String name, String value) {
            cookie(name, value, null);
        }
        
        void cookie(String name, String value, Date expires);

        void encoder(String contentType, Function<ChainedHttpConfig.ChainedRequest,HttpEntity> val);
        void encoder(List<String> contentTypes, Function<ChainedHttpConfig.ChainedRequest,HttpEntity> val);
        Function<ChainedHttpConfig.ChainedRequest,HttpEntity> encoder(String contentType);
    }
    
    public interface Response {
        void when(Status status, Closure<Object> closure);
        void when(Integer code, Closure<Object> closure);
        void when(String code, Closure<Object> closure);
        Closure<Object> when(Integer code);

        void setSuccess(Closure<Object> closure);
        void setFailure(Closure<Object> closure);

        void parser(String contentType, Function<HttpResponse,Object> val);
        void parser(List<String> contentTypes, Function<HttpResponse,Object> val);
        Function<HttpResponse,Object> parser(String contentType);
    }

    Request getRequest();
    Response getResponse();
}

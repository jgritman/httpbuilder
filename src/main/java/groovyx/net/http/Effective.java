package groovyx.net.http;

import java.nio.charset.Charset;
import java.util.Map;
import groovy.lang.Closure;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import java.util.function.Function;

public interface Effective {

    public interface Request {
        Charset effectiveCharset();
        String effectiveContentType();
        Object effectiveBody();
        URIBuilder effectiveUri();
        Map<String,String> effectiveHeaders(Map<String,String> val);
    }

    public interface Response {
        Closure<Object> effectiveAction(Integer code);
    }

    Function<HttpResponse,Object> effectiveParser(String contentType);
    Function<Request,HttpEntity> effectiveEncoder(String contentType);
}

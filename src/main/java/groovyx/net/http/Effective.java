package groovyx.net.http;

import groovy.lang.Closure;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

public interface Effective {

    public interface Req {
        Charset effectiveCharset();
        String effectiveContentType();
        Object effectiveBody();
        URIBuilder effectiveUri();
        Map<String,String> effectiveHeaders(Map<String,String> val);
    }

    public interface Resp {
        Closure<Object> effectiveAction(Integer code);
    }

    Function<HttpResponse,Object> effectiveParser(String contentType);
    Function<Req,HttpEntity> effectiveEncoder(String contentType);
    Set<String> acceptHeader(final String contentType);
    Req getReq();
    Resp getResp();
}

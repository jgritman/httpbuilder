package groovyx.net.http;

import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import java.util.List;
import groovy.lang.Script;
import groovy.lang.Binding;
import java.util.function.Function;

abstract public class HttpConfigScript extends Script implements HttpConfig {

    public static final String TARGET = "target";
    
    protected HttpConfig getTarget() {
        return (HttpConfig) getBinding().getVariable(TARGET);
    }

    public void encoder(String contentType, Function<Effective.Req,HttpEntity> val) {
        getTarget().encoder(contentType, val);
    }
    
    public void encoder(List<String> contentTypes, Function<Effective.Req,HttpEntity> val) {
        getTarget().encoder(contentTypes, val);
    }
    
    public Function<Effective.Req,HttpEntity> encoder(String contentType) {
        return getTarget().encoder(contentType);
    }

    public void parser(String contentType, Function<HttpResponse,Object> val) {
        getTarget().parser(contentType, val);
    }
    
    public void parser(List<String> contentTypes, Function<HttpResponse,Object> val) {
        getTarget().parser(contentTypes, val);
    }
    
    public Function<HttpResponse,Object> parser(String contentType) {
        return getTarget().parser(contentType);
    }
    
    public Request getRequest() {
        return getTarget().getRequest();
    }
    
    public Response getResponse() {
        return getTarget().getResponse();
    }
}

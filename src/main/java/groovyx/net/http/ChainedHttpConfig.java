package groovyx.net.http;

import java.util.function.Function;
import java.util.function.Predicate;
import static groovyx.net.http.Traverser.*;
import org.apache.http.cookie.Cookie;
import java.util.List;
import java.util.Map;
import java.nio.charset.Charset;
import groovy.lang.Closure;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

public interface ChainedHttpConfig extends HttpConfig {

    public interface ChainedRequest extends Request {
        ChainedRequest getParent();
        List<Cookie> getCookies();
        Object getBody();
        String getContentType();
        Map<String,Function<ChainedRequest,HttpEntity>> getEncoderMap();
        Charset getCharset();

        default Charset actualCharset() {
            return traverse(this, (cr) -> cr.getParent(), (cr) -> cr.getCharset(), Traverser::notNull);
        }
        
        default String actualContentType() {
            return traverse(this, (cr) -> cr.getParent(), (cr) -> cr.getContentType(), Traverser::notNull);
        }

        default Object actualBody() {
            return traverse(this, (cr) -> cr.getParent(), (cr) -> cr.getBody(), Traverser::notNull);
        }

        default Map<String,String> actualHeaders(final Map<String,String> map) {
            Predicate<Map<String,String>> addValues = (headers) -> { map.putAll(headers); return false; };
            traverse(this, (cr) -> cr.getParent(), (cr) -> cr.getHeaders(), addValues);
            return map;
        }

        default Function<ChainedRequest,HttpEntity> actualEncoder(final String contentType) {
            return traverse(this, (cr) -> cr.getParent(), (cr) -> cr.encoder(contentType), Traverser::notNull);
        }

        default Auth actualAuth() {
            final Predicate<Auth> choose = (a) -> a != null && a.getAuthType() != null;
            return traverse(this, (cr) -> cr.getParent(), (cr) -> cr.getAuth(), choose);
        }

        default List<Cookie> actualCookies(final List<Cookie> list) {
            Predicate<List<Cookie>> addAll = (cookies) -> { list.addAll(cookies); return false; };
            traverse(this, (cr) -> cr.getParent(), (cr) -> cr.getCookies(), addAll);
            return list;
        }
    }

    public interface ChainedResponse extends Response {
        ChainedResponse getParent();

        default Closure<Object> actualAction(final Integer code) {
            return traverse(this, (cr) -> cr.getParent(), (cr) -> cr.when(code), Traverser::notNull);
        }
        
        default Function<HttpResponse,Object> actualParser(final String contentType) {
            return traverse(this, (cr) -> cr.getParent(), (cr) -> cr.parser(contentType), Traverser::notNull);
        }
    }

    ChainedResponse getChainedResponse();
    ChainedRequest getChainedRequest();
    ChainedHttpConfig getParent();
}

package groovyx.net.http;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

public class HttpContentType {

    private static final AtomicInteger counter = new AtomicInteger();
    private static final ConcurrentMap<String,HttpContentType> all = new ConcurrentHashMap<>();
    
    private final int id;
    private final String[] types;
    
    public HttpContentType(final String... types) {
        for(String t : types) {
            if(all.containsKey(t)) {
                throw new IllegalArgumentException(t + " has already been specified as a content type");
            }
        }
        
        this.id = counter.incrementAndGet();
        this.types = types;

        for(String t : types) {
            all.put(t, this);
        }
    }

    @Override
    public String toString() {
        return types[0];
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(final Object rhs) {
        if(!(rhs instanceof HttpContentType)) {
            return false;
        }

        return ((HttpContentType) rhs).id == id;
    }

    public static HttpContentType find(final String type) {
        return all.get(type);
    }

    public String getAcceptHeader() {
        return String.join(", ", types);
    }

    //commonly used content types
    public static final HttpContentType ANY = new HttpContentType("*/*");
    public static final HttpContentType TEXT = new HttpContentType("text/plain");
    public static final HttpContentType JSON = new HttpContentType("application/json","application/javascript","text/javascript");
    public static final HttpContentType XML = new HttpContentType("application/xml","text/xml","application/xhtml+xml","application/atom+xml");
    public static final HttpContentType HTML = new HttpContentType("text/html");
    public static final HttpContentType URLENC = new HttpContentType("application/x-www-form-urlencoded");
    public static final HttpContentType BINARY = new HttpContentType("application/octet-stream");
}

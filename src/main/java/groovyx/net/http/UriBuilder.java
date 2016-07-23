package groovyx.net.http;

import java.util.Collections;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static java.util.Objects.equals;
import org.apache.http.client.utils.URIBuilder;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import static groovyx.net.http.Traverser.*;

public abstract class UriBuilder {

    public static final int DEFAULT_PORT = -1;

    public abstract UriBuilder setScheme(String val);
    public abstract String getScheme();
    public abstract UriBuilder setPort(int val);
    public abstract int getPort();
    public abstract UriBuilder setHost(String val);
    public abstract String getHost();
    public abstract UriBuilder setPath(String val);
    public abstract String getPath();
    public abstract UriBuilder setQuery(Map<String,Object> val);
    public abstract Map<String,Object> getQuery();
    public abstract UriBuilder setFragment(String val);
    public abstract String getFragment();
    public abstract UriBuilder setUserInfo(String val);
    public abstract String getUserInfo();
    public abstract UriBuilder getParent();
    
    public URI toURI(final Charset charset) {
        try {
            final URIBuilder b = new URIBuilder();
            
            final String scheme = traverse(this, (u) -> u.getParent(), (u) -> u.getScheme(), Traverser::notNull);
            if(scheme != null) b.setScheme(scheme);
            
            final Integer port = traverse(this, (u) -> u.getParent(), (u) -> u.getPort(), notValue(DEFAULT_PORT));
            if(port != null) b.setPort(port);
            
            final String host = traverse(this, (u) -> u.getParent(), (u) -> u.getHost(), Traverser::notNull);
            if(host != null) b.setHost(host);
            
            final String path = traverse(this, (u) -> u.getParent(), (u) -> u.getPath(), Traverser::notNull);
            if(path != null) b.setPath(path);
            
            final Map<String,Object> query = traverse(this, (u) -> u.getParent(), (u) -> u.getQuery(), Traverser::notNull);
            if(query != null) {
                for(Map.Entry<String,Object> entry : query.entrySet()) {
                    b.addParameter(entry.getKey(), entry.getValue().toString());
                }
            }
            
            final String fragment = traverse(this, (u) -> u.getParent(), (u) -> u.getFragment(), Traverser::notNull);
            if(fragment != null) b.setFragment(fragment);
            
            final String userInfo = traverse(this, (u) -> u.getParent(), (u) -> u.getUserInfo(), Traverser::notNull);
            if(userInfo != null) b.setUserInfo(userInfo);
            
            return b.build();
        }
        catch(URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected final void populateFrom(final URIBuilder builder) {
        setScheme(builder.getScheme());
        setPort(builder.getPort());
        setHost(builder.getHost());
        setPath(builder.getPath());

        Map<String,Object> tmp = new LinkedHashMap<>();
        for(NameValuePair nvp : builder.getQueryParams()) {
            tmp.put(nvp.getName(), nvp.getValue());
        }
        setQuery(tmp);

        setFragment(builder.getFragment());
        setUserInfo(builder.getUserInfo());
    }

    public final UriBuilder setFull(final String str) throws URISyntaxException {
        final URIBuilder builder = new URIBuilder(str);
        populateFrom(builder);
        return this;
    }

    public final UriBuilder setFull(final URI uri) {
        final URIBuilder builder = new URIBuilder(uri);
        populateFrom(builder);
        return this;
    }

    public URI toURI() {
        return toURI(StandardCharsets.UTF_8);
    }

    public static UriBuilder basic(final UriBuilder parent) {
        return new Basic(parent);
    }

    public static UriBuilder threadSafe(final UriBuilder parent) {
        return new ThreadSafe(parent);
    }

    public static UriBuilder root() {
        return new ThreadSafe(null);
    }

    private static final class Basic extends UriBuilder {
        private String scheme;
        public UriBuilder setScheme(String val) { scheme = val; return this; }
        public String getScheme() { return scheme; }

        private int port = DEFAULT_PORT;
        public UriBuilder setPort(int val) { port = val; return this; }
        public int getPort() { return port; }

        private String host;
        public UriBuilder setHost(String val) { host = val; return this; }
        public String getHost() { return host; }

        private String path;
        public UriBuilder setPath(String val) { path = val; return this; }
        public String getPath() { return path; }
        
        private Map<String,Object> query = new LinkedHashMap<>(1);
        public UriBuilder setQuery(Map<String,Object> val) { query.putAll(val);; return this; }
        public Map<String,Object> getQuery() { return query; }

        private String fragment;
        public UriBuilder setFragment(String val) { fragment = val; return this; }
        public String getFragment() { return fragment; }

        private String userInfo;
        public UriBuilder setUserInfo(String val) { userInfo = val; return this; }
        public String getUserInfo() { return userInfo; }

        private final UriBuilder parent;
        public UriBuilder getParent() { return parent; }

        public Basic(final UriBuilder parent) {
            this.parent = parent;
        }
    }

    private static final class ThreadSafe extends UriBuilder {
        private volatile String scheme;
        public UriBuilder setScheme(String val) { scheme = val; return this; }
        public String getScheme() { return scheme; }

        private volatile int port = DEFAULT_PORT;
        public UriBuilder setPort(int val) { port = val; return this; }
        public int getPort() { return port; }

        private volatile String host;
        public UriBuilder setHost(String val) { host = val; return this; }
        public String getHost() { return host; }

        private volatile String path;
        public UriBuilder setPath(String val) { path = val; return this; }
        public String getPath() { return path; }

        private Map<String,Object> query = new ConcurrentHashMap();
        public UriBuilder setQuery(Map<String,Object> val) { query.putAll(val); return this; }
        public Map<String,Object> getQuery() { return query; }

        private volatile String fragment;
        public UriBuilder setFragment(String val) { fragment = val; return this; }
        public String getFragment() { return fragment; }

        private volatile String userInfo;
        public UriBuilder setUserInfo(String val) { userInfo = val; return this; }
        public String getUserInfo() { return userInfo; }

        private final UriBuilder parent;
        public UriBuilder getParent() { return parent; }

        public ThreadSafe(final UriBuilder parent) {
            this.parent = parent;
        }
    }
}

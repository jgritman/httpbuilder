package groovyx.net.http;

import java.util.Map;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import groovy.lang.Closure;

public abstract class HandlerChain {

    public abstract HandlerChain getParent();

    public abstract HttpContentType getContentType();
    public abstract void setContentType(final HttpContentType type);
    public void setContentType(final String type) { setContentType(HttpContentType.find(type)); };

    public abstract HttpContentType getRequestContentType();
    public abstract void setRequestContentType(final HttpContentType type);
    public void setRequestContentType(final String type) { setRequestContentType(HttpContentType.find(type)); };

    public abstract URIBuilder getUri();
    public abstract void setUri(final URIBuilder ub);

    public void setUri(final String str) {
        try {
            setUri(new URIBuilder(new URI(str)));
        }
        catch(URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void setUri(final URI val) {
        setUri(new URIBuilder(val));
    }

    public void setUri(final URL val) {
        try {
            setUri(new URIBuilder(val.toURI()));
        }
        catch(URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract Map<HttpContentType,Closure> getResponseHandlers();
    public abstract Map<String,String> getHeaders();

    public abstract Object getBody();
    public abstract void setBody(final Object o);

    private static class Default {
        public HandlerChain getParent() { return null; }

        private volatile HttpContentType contentType = HttpContentType.ANY;
        public HttpContentType getContentType() { return contentType; }
        public abstract void setContentType(final HttpContentType val) { contentType = val; }

        private volatile HttpContentType requestContentType = HttpContentType.ANY;
        public HttpContentType getRequestContentType() { return requestContentType; }
        public void setRequestContentType(final HttpContentType val) { requestContentType = val; }

        private volatile URIBuilder uri;
        public URIBuilder getUri() { return uri; }
        public void setUri(final URIBuilder val) { uri = val; }
        
        public abstract Map<HttpContentType,Closure> getResponseHandlers();
        public abstract Map<String,String> getHeaders();
        
        public Object getBody() { return null; }
        public void setBody(final Object o) {
            throw new UnsupportedOperationException("You can't set the body on the default HandlerChain");
        }

    }
}

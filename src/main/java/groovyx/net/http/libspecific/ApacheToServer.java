package groovyx.net.http.libspecific;

import groovyx.net.http.ToServer;
import java.io.InputStream;

public class ApacheToServer implements ToServer {

    private String contentType;
    private InputStream inputStream;
    
    public void setContentType(final String val) {
        contentType = val;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setInputStream(final InputStream val) {
        inputStream = val;
    }
    
    public InputStream getInputStream() {
        return inputStream;
    }
}

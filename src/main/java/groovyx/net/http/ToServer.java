package groovyx.net.http;

import java.io.InputStream;

public interface ToServer {
    void setContentType(String type);
    String getContentType();
    void setInputStream(InputStream is);
    InputStream getInputStream();
}

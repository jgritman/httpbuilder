package groovyx.net.http;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.BasicCookieStore;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import static groovyx.net.http.HttpConfigs.*;

public class HttpObjectConfigImpl implements HttpObjectConfig {

    final ChainedHttpConfig config = basic(root());
    final Exec exec = new Exec();

    private static class Exec implements Execution {
        int maxThreads = 1;
        Executor executor;
        SSLContext sslContext;

        public void setMaxThreads(final int val) {
            if(val < 1) {
                throw new IllegalArgumentException("Max Threads cannot be less than 1");
            }
            
            this.maxThreads = val;
        }

        public void setExecutor(final Executor val) {
            this.executor = val;
        }

        public void setSslContext(final SSLContext val) {
            this.sslContext = val;
        }
    }

    private static class SingleThreaded implements Executor {
        public void execute(Runnable r) {
            r.run();
        }

        public static final SingleThreaded instance = new SingleThreaded();
    }

    public HttpBuilder build() {
        final BasicCookieStore cookieStore = new BasicCookieStore();
        final Executor e = exec.executor == null ? SingleThreaded.instance : exec.executor;
        HttpClientBuilder myBuilder = HttpClients.custom().setDefaultCookieStore(cookieStore);
        ChainedHttpConfig myConfig = config;
        
        if(exec.maxThreads > 1) {
            final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
            cm.setMaxTotal(exec.maxThreads);
            cm.setDefaultMaxPerRoute(exec.maxThreads);
            
            myBuilder.setConnectionManager(cm);
            myConfig = new HttpConfigs.ThreadSafeHttpConfig(config);
        }
        
        if(exec.sslContext != null) {
            myBuilder.setSSLContext(exec.sslContext);
        }
        
        return new HttpBuilder(myBuilder.build(), cookieStore, myConfig, e);
    }

    public Request getRequest() {
        return config.getRequest();
    }

    public Response getResponse() {
        return config.getResponse();
    }

    public HttpConfig getParent() {
        return config.getParent();
    }

    public Execution getExecution() {
        return exec;
    }
}

package groovyx.net.http;

import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;

public interface HttpObjectConfig extends HttpConfig {

    public interface Execution {
        void setMaxThreads(int val);
        void setExecutor(Executor val);
        void setSslContext(SSLContext val);
    }

    Execution getExecution();
}


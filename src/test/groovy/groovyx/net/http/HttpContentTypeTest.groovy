package groovyx.net.http;

import spock.lang.*;
import static groovyx.net.http.HttpContentType.*;

class HttpContentTypeTest extends Specification {

    def "Equals"() {
        expect:
        ANY == ANY;
        TEXT != JSON;
    }

    def "Constructor"() {
        when:
        new HttpContentType("application/octet-stream");

        then:
        thrown(IllegalArgumentException);
    }

    def "Hash Code"() {
        expect:
        ANY.hashCode() == ANY.hashCode();
        TEXT.hashCode() != JSON.hashCode();
    }

    def "Extend"() {
        setup:
        def CSV = new HttpContentType("text/csv");

        expect:
        CSV == CSV;
        CSV != XML;
    }

    def "Find"() {
        expect:
        BINARY == find("application/octet-stream");
        XML != find("application/json");
        null == find("foo/bar");
    }
}

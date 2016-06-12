package groovyx.net.http;

import java.util.List;
import java.util.Arrays;

public class ContentTypes {
    public static final List<String> ANY = Arrays.asList("*/*");
    public static final List<String> TEXT = Arrays.asList("text/plain");
    public static final List<String> JSON = Arrays.asList("application/json","application/javascript","text/javascript");
    public static final List<String> XML = Arrays.asList("application/xml","text/xml","application/xhtml+xml","application/atom+xml");
    public static final List<String> HTML = Arrays.asList("text/html");
    public static final List<String> URLENC = Arrays.asList("application/x-www-form-urlencoded");
    public static final List<String> BINARY = Arrays.asList("application/octet-stream");
}

package groovyx.net.http;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.Collections;
import java.nio.charset.Charset;

public class Form {

    private static class Buffers {
        final StringBuilder builder = new StringBuilder(1024);
        final char[] buffer = new char[1024];

        public void clear() {
            builder.setLength(0);
        }
    }

    private static ThreadLocal<Buffers> tlBuffers = new ThreadLocal<Buffers>() {
            @Override protected Buffers initialValue() {
                return new Buffers();
            }
        };

    private static void toBuffers(final InputStream is, final Charset charset) throws IOException {
        tlBuffers.get().clear();
        final Reader reader = new InputStreamReader(is, charset);
        final StringBuilder builder = tlBuffers.get().builder;
        final char[] buffer = tlBuffers.get().buffer;
        
        int total = 0;
        while((total = reader.read(buffer, 0, 1024)) != -1) {
            builder.append(buffer, 0, total);
        }
    }
    
    public static Map<String,List<String>> decode(final InputStream is, final Charset charset) {
        try {
            final Map<String,List<String>> ret = new LinkedHashMap<>();
            toBuffers(is, charset);
            final StringBuilder builder = tlBuffers.get().builder;
            
            int lower = 0;
            while(lower < builder.length()) {
                int eqAt = builder.indexOf("=", lower);
                int ampAt = builder.indexOf("&", eqAt);
                if(ampAt == -1) {
                    ampAt = builder.length();
                }
                
                final String key = URLDecoder.decode(builder.substring(lower, eqAt), charset.toString());
                final String value = URLDecoder.decode(builder.substring(eqAt+1, ampAt), charset.toString());
                List<String> values = ret.get(key);
                if(values == null) {
                    values = new ArrayList<>();
                    ret.put(key, values);
                }

                if(ampAt - eqAt > 1) {
                    values.add(value);
                }
                
                lower = ampAt + 1;
            }
            
            return ret;
        }
        catch(IOException e) {
            throw new RuntimeException("Error in decoding form", e);
        }
    }

    public static List<?> checkValue(final Object v) {
        if(v instanceof List) {
            List list = (List) v;
            if(list.size() > 0) {
                return list;
            }
            else {
                return Collections.singletonList("");
            }
        }
        else if(v != null) {
            return Collections.singletonList(v);
        }
        else {
            return Collections.singletonList("");
        }
    }
    
    public static String encode(final Map<?,?> map, final Charset charset) {
        try {
            tlBuffers.get().clear();
            final StringBuilder builder = tlBuffers.get().builder;
            for(Map.Entry<?,?> entry : map.entrySet()) {
                for(Object value : checkValue(entry.getValue())) {
                    builder.append(URLEncoder.encode(entry.getKey().toString(), charset.toString())).append("=");
                    builder.append(URLEncoder.encode(value.toString(), charset.toString())).append("&");
                }
            }
            
            return builder.substring(0, builder.length() -1);
        }
        catch(UnsupportedEncodingException e) {
            throw new RuntimeException("Error in encoding form", e);
        }
    }
}

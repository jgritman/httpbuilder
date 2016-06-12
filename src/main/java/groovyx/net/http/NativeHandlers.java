package groovyx.net.http;

import org.apache.http.util.EntityUtils;
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.Writable;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import groovy.xml.StreamingMarkupBuilder;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class NativeHandlers {

    private static final Logger log = LoggerFactory.getLogger(NativeHandlers.class);
    
    public static Object success(final HttpResponse response, final Object data) throws IOException {
        //If response is streaming, buffer it in a byte array:
        if(data instanceof InputStream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DefaultGroovyMethods.leftShift(buffer, (InputStream) data);
            return new ByteArrayInputStream(buffer.toByteArray());
        }
        else if(data instanceof Reader) {
            StringWriter buffer = new StringWriter();
            DefaultGroovyMethods.leftShift(buffer, (Reader) data);
            return new StringReader(buffer.toString());
        }
        else if(data instanceof Closeable) {
            if(log.isWarnEnabled()) {
                log.warn("Parsed data is streaming, but will be accessible after " +
                         "the network connection is closed.  Use at your own risk!");
            }
        }
        
        return data;
    }

    public static Object failure(final HttpResponse response) throws HttpResponseException {
        throw new HttpResponseException(response);
    }
    
    public static class Encoders {

        private static Object checkNull(final Object body) {
            if(body == null) {
                throw new NullPointerException("Effective body cannot be null");
            }

            return body;
        }

        private static void checkTypes(String contentType, final Object body, final Class[] allowedTypes) {
            if(contentType == null) {
                throw new IllegalArgumentException("Content Type is null");
            }
            
            Class type = body.getClass();
            for(Class allowed : allowedTypes) {
                if(allowed.isAssignableFrom(type)) {
                    return;
                }
            }

            final String msg = String.format("Cannot encode bodies of type %s, only bodies of: %s",
                                             type.getName(),
                                             Arrays.stream(allowedTypes).map(Class::getName).collect(Collectors.joining(", ")));

            throw new IllegalArgumentException(msg);
        }

        private static final Class[] BINARY_TYPES = new Class[] { ByteArrayInputStream.class, InputStream.class,
                                                                  byte[].class, ByteArrayOutputStream.class, Closure.class };
        
        public static HttpEntity binary(final Effective.Req request) {
            final Object body = checkNull(request.effectiveBody());
            final String contentType = request.effectiveContentType();
            checkTypes(contentType, body, BINARY_TYPES);
            
            InputStreamEntity entity = null;
            
            if(body instanceof ByteArrayInputStream) {
                // special case for ByteArrayIS so that we can set the content length.
                ByteArrayInputStream in = (ByteArrayInputStream) body;
                entity = new InputStreamEntity(in, in.available());
            }
            else if(body instanceof InputStream) {
                entity = new InputStreamEntity((InputStream) body);
            }
            else if(body instanceof byte[]) {
                final byte[] out = (byte[]) body;
                entity = new InputStreamEntity(new ByteArrayInputStream(out), out.length);
            }
            else if(body instanceof ByteArrayOutputStream ) {
                ByteArrayOutputStream out = (ByteArrayOutputStream) body;
                entity = new InputStreamEntity(new ByteArrayInputStream(out.toByteArray()), out.size());
            }
            else if(body instanceof Closure) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                ((Closure) body).call(out);
                entity = new InputStreamEntity(new ByteArrayInputStream(out.toByteArray()), out.size());
            }
            else {
                throw new UnsupportedOperationException();
            }
            
            entity.setContentType(contentType);
            return entity;
        }

        private static final Class[] TEXT_TYPES = new Class[] { Closure.class, Writable.class, Reader.class, String.class };

        public static HttpEntity text(final Effective.Req request) throws IOException {
            final Object body = checkNull(request.effectiveBody());
            final String contentType = request.effectiveContentType();
            checkTypes(contentType, body, TEXT_TYPES);
            String text = null;
            
            if(body instanceof Closure) {
                final StringWriter out = new StringWriter();
                final PrintWriter writer = new PrintWriter( out );
                ((Closure) body).call(writer);
                writer.close();
                out.flush();
                text = out.toString();
            }
            else if(body instanceof Writable) {
                final StringWriter out = new StringWriter();
                ((Writable) body).writeTo(out);
                out.flush();
                text = out.toString();
            }
            else if(body instanceof Reader) {
                final BufferedReader buffered = (body instanceof BufferedReader ?
                                           (BufferedReader) body :
                                           new BufferedReader((Reader) body));
                StringWriter out = new StringWriter();
                DefaultGroovyMethods.leftShift(out, buffered);
                text = out.toString();
            }
            else {
                throw new UnsupportedOperationException();
            }

            final StringEntity ret = new StringEntity(text, request.effectiveCharset());
            ret.setContentType(contentType);
            return ret;
        }

        private static final Class[] FORM_TYPES = { Map.class, String.class };

        public static HttpEntity form(final Effective.Req request) {
            final Object body = checkNull(request.effectiveBody());
            final String contentType = request.effectiveContentType();
            checkTypes(contentType, body, FORM_TYPES);

            if(body instanceof String) {
                final StringEntity ret = new StringEntity(body.toString(), request.effectiveCharset());
                ret.setContentType(contentType);
                return ret;
            }
            else if(body instanceof Map) {
                final Map<Object,Object> params = (Map<Object,Object>) body;
                final List<NameValuePair> paramList = new ArrayList<NameValuePair>();
                for(Map.Entry<Object,Object> entry : params.entrySet()) {
                    if(entry.getValue() instanceof List) {
                        for(Object subVal : (List<?>) entry.getValue()){
                            paramList.add(new BasicNameValuePair(entry.getKey().toString(),
                                                                 (subVal == null) ? "" : subVal.toString()));
                        }
                    }
                    else {
                        paramList.add(new BasicNameValuePair(entry.getKey().toString(),
                                                             (entry.getValue() == null) ? "" : entry.getValue().toString()));
                    }
                }

                final UrlEncodedFormEntity ret = new UrlEncodedFormEntity(paramList, request.effectiveCharset());
                ret.setContentType(contentType);
                return ret;
            }
            else {
                throw new UnsupportedOperationException();
            }
        }

        private static final Class[] XML_TYPES = new Class[] { String.class, StreamingMarkupBuilder.class };
        
        public static HttpEntity xml(final Effective.Req request) {
            final Object body = checkNull(request.effectiveBody());
            final String contentType = request.effectiveContentType();
            checkTypes(contentType, body, XML_TYPES);

            StringEntity ret;
            if(body instanceof String) {
                ret = new StringEntity(body.toString(), request.effectiveCharset());
            }
            else if(body instanceof Closure) {
                final StreamingMarkupBuilder smb = new StreamingMarkupBuilder();
                ret = new StringEntity(smb.bind(body).toString(), request.effectiveCharset());
            }
            else {
                throw new UnsupportedOperationException();
            }

            ret.setContentType(contentType);
            return ret;
        }

        public static HttpEntity json(final Effective.Req request) {
            final Object body = checkNull(request.effectiveBody());
            final String contentType = request.effectiveContentType();
            final String json = ((body instanceof String || body instanceof GString)
                                 ? body.toString()
                                 : new JsonBuilder(body).toString());
            final StringEntity ret = new StringEntity(json, request.effectiveCharset());
            ret.setContentType(contentType);
            return ret;
        }
    }

    public static class Parsers {

        public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
        private static final Logger log = LoggerFactory.getLogger(Parsers.class);
            /**
             * This CatalogResolver is static to avoid the overhead of re-parsing
             * the catalog definition file every time.  Unfortunately, there's no
             * way to share a single Catalog instance between resolvers.  The
             * {@link Catalog} class is technically not thread-safe, but as long as you
             * do not parse catalog files while using the resolver, it should be fine.
             */
        protected static CatalogResolver catalogResolver;
        
        static {
            CatalogManager catalogManager = new CatalogManager();
            catalogManager.setIgnoreMissingProperties( true );
            catalogManager.setUseStaticCatalog( false );
            catalogManager.setRelativeCatalogs( true );
            
            try {
                catalogResolver = new CatalogResolver( catalogManager );
                catalogResolver.getCatalog().parseCatalog(
                                                          ParserRegistry.class.getResource( "/catalog/html.xml" ) );
            }
            catch(IOException ex) {
                if(log.isWarnEnabled()) {
                    log.warn("Could not resolve default XML catalog", ex);
                }
            }
        }
        
        /**
         * Helper method to get the charset from the response.  This should be done
         * when manually parsing any text response to ensure it is decoded using the
         * correct charset. For instance:<pre>
         * Reader reader = new InputStreamReader( resp.getEntity().getContent(),
         *   ParserRegistry.getCharset( resp ) );</pre>
         * @param resp
         */
        public static Charset charset(final HttpResponse resp) {
            try {
                NameValuePair charset = resp.getEntity().getContentType()
                    .getElements()[0].getParameterByName("charset");
                
                if(charset == null || charset.getValue().trim().equals("")) {
                    if(log.isDebugEnabled()) {
                        log.debug("Could not find charset in response; using " + DEFAULT_CHARSET);
                    }
                    
                    return DEFAULT_CHARSET;
                }
                
                return Charset.forName(charset.getValue());
            }
            catch ( RuntimeException ex ) { // NPE or OOB Exceptions
                if(log.isWarnEnabled()) {
                    log.warn( "Could not parse charset from content-type header in response" );
                }
                
                return DEFAULT_CHARSET;
            }
        }

        public static Object nothing(final HttpResponse response) {
            return null;
        }

        public static InputStream stream(final HttpResponse response) {
            try {
                return response.getEntity().getContent();
            }
            catch(IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        public static byte[] streamToBytes(final HttpResponse response) {
            try {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DefaultGroovyMethods.leftShift(baos, stream(response));
                return baos.toByteArray();
            }
            catch(IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        public static Reader text(final HttpResponse response) {
            return new InputStreamReader(stream(response), charset(response));
        }

        public static String textToString(final HttpResponse response) {
            return text(response).toString();
        }

        public static Map<String,String> form(final HttpResponse response) {
            try {
                final HttpEntity entity = response.getEntity();
                List<NameValuePair> params = URLEncodedUtils.parse(entity);
                Map<String,String> paramMap = new HashMap<String,String>(params.size());
                for(NameValuePair param : params) {
                    paramMap.put(param.getName(), param.getValue());
                }
                
                return paramMap;
            }
            catch(IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        public static GPathResult html(final HttpResponse response) {
            try {
                final XMLReader p = new org.cyberneko.html.parsers.SAXParser();
                p.setEntityResolver(catalogResolver);
                return new XmlSlurper(p).parse(text(response));
            }
            catch(IOException | SAXException ex) {
                throw new RuntimeException(ex);
            }
        }

        public static GPathResult xml(final HttpResponse response) {
            try {
                final XmlSlurper xml = new XmlSlurper();
                xml.setEntityResolver(catalogResolver);
                xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
                xml.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                return xml.parse(text(response));
            }
            catch(IOException | SAXException | ParserConfigurationException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        public static Object json(final HttpResponse response) {
            // there is a bug in the JsonSlurper.parse method...
            //String jsonTxt = DefaultGroovyMethods.getText( parseText( resp ) );
            return new JsonSlurper().parse(text(response));
        }
    }
}

import java.nio.charset.StandardCharsets

request.with {
    charset = StandardCharsets.UTF_8
    uri = 'http://www.google.com'
    contentType = 'application/json'
    encoder BINARY, NativeHandlers.Encoders.&binary
    encoder TEXT, NativeHandlers.Encoders.&text
    encoder URLENC, NativeHandlers.Encoders.&form
    encoder XML, NativeHandlers.Encoders.&xml
    encoder JSON, NativeHandlers.Encoders.&json
}

response.with {
    success = NativeHandlers.&success
    failure = NativeHandlers.&failure
    
    
    parser BINARY, NativeHandlers.Parsers.&binary
    parser TEXT, NativeHandlers.Parsers.&text
    parser URLENC, NativeHandlers.Parsers.&form
    parser XML, NativeHandlers.Parsers.&xml
    parser JSON, NativeHandlers.Parsers.&json
}



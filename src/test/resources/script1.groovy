import java.nio.charset.StandardCharsets

request.charset = StandardCharsets.UTF_8
request.uri = 'http://www.google.com'
request.contentType = 'application/json'

response.success = NativeHandlers.&success
response.failure = NativeHandlers.&failure

encoder BINARY, NativeHandlers.Encoders.&binary
encoder TEXT, NativeHandlers.Encoders.&text
encoder URLENC, NativeHandlers.Encoders.&form
encoder XML, NativeHandlers.Encoders.&xml
encoder JSON, NativeHandlers.Encoders.&json

parser BINARY, NativeHandlers.Parsers.&binary
parser TEXT, NativeHandlers.Parsers.&text
parser URLENC, NativeHandlers.Parsers.&form
parser XML, NativeHandlers.Parsers.&xml
parser JSON, NativeHandlers.Parsers.&json



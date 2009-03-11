// Send a Twitter update when a release is made.  Cool!

import groovyx.net.http.HTTPBuilder

import static groovyx.net.http.Method.*
import static groovyx.net.http.ContentType.*

def http = new HTTPBuilder('http://twitter.com/statuses/')

http.auth.basic pom.properties.'twitter.user', 
				pom.properties.'twitter.passwd'

def msg = "HTTPBuilder v${pom.version} has been released!"

println "Tweeting release for v${pom.version}..."

http.request( POST, XML ) { req ->
	uri.path = 'update.xml'
	send URLENC, [status:msg, source:'httpbuilder']
	
	// twitter doesn't like the Expect: 100 header...
	req.params.setBooleanParameter 'http.protocol.expect-continue', false
	
	 response.success = { resp, xml ->
		println "Tweet response status: ${resp.statusLine}"
		assert resp.statusLine.statusCode == 200
	}
}

// Send a Twitter update when a release is made.  Cool!

print 'testtesttesttesttesttesttestesttesttest'
import groovyx.net.http.HTTPBuilder

import static groovyx.net.http.Method.*
import static groovyx.net.http.ContentType.*

def http = new HTTPBuilder('http://twitter.com/statuses/')

http.auth.oauth pom.properties.'twitter.oauth.consumerKey',
				pom.properties.'twitter.oauth.consumerSecret',
				pom.properties.'twitter.oauth.accessToken',
				pom.properties.'twitter.oauth.secretToken'

def msg = "v${pom.version} has been released! (${new Date()}) http://goo.gl/VzuT #groovy"

println "Tweeting release for v${pom.version}..."

http.request( POST, XML ) { req ->
	uri.path = 'update.xml'
	send URLENC, [status:msg, source:'httpbuilder']
	
	response.success = { resp, xml ->
		println "Tweet response status: ${resp.statusLine}"
		assert resp.statusLine.statusCode == 200
	}
}

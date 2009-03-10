/* Example script that demonstrates use of the Twitter API.  
 * 
 */

import groovyx.net.http.RESTClient
import groovy.util.slurpersupport.GPathResult
import static groovyx.net.http.ContentType.URLENC

twitter = new RESTClient( 'https://twitter.com/statuses/' )
twitter.auth.basic System.getProperty( 'user' ), System.getProperty( 'passwd' )


// Test a URL using the HEAD method:

try { // expect an exception from a 404 response:
	twitter.head path : 'public_timeline'
	assert false, 'Expected exception'
}
/* The exception is used for flow control but can be used 
   to read the response: */
catch( ex ) { assert ex.response.status == 404 }

assert twitter.head( path : 'public_timeline.json' ).status == 200



// GET our friends' timeline:

def resp = twitter.get( path : 'friends_timeline.json' )
assert resp.status == 200
assert resp.contentType == JSON.toString()
assert ( resp.data instanceof net.sf.json.JSON )
assert resp.data.status.size() > 0


// POST a status update to twitter!

def msg = "I'm using HTTPBuilder's RESTClient on ${new Date()}"
	
resp = twitter.post( path : 'update.xml', 
		body : [ status:msg, source:'httpbuilder' ],
		requestContentType : URLENC )

assert resp.status == 200
assert ( resp.data instanceof GPathResult ) // parsed using XmlSlurper 
assert resp.data.text == msg
assert resp.data.user.screen_name == userName
def postID = resp.data.id


// Now let's delete that post:

resp = twitter.delete( path : "destroy/${postID}.json" )
assert resp.status == 200
assert resp.data.id == postID
println "Test tweet ID ${resp.data.id} was deleted."
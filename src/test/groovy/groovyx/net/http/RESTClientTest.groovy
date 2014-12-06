package groovyx.net.http

import groovy.util.slurpersupport.GPathResult
import org.apache.http.params.HttpConnectionParams
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static groovyx.net.http.ContentType.*

/**
 * @author tnichols
 *
 */
public class RESTClientTest {

    def twitter = null
    static postID = null
    def userID = System.getProperty('twitter.user')

    @Before
    public void setUp() {
        twitter = new RESTClient('https://api.twitter.com/1.1/statuses/')
        twitter.auth.oauth System.getProperty('twitter.oauth.consumerKey'),
                System.getProperty('twitter.oauth.consumerSecret'),
                System.getProperty('twitter.oauth.accessToken'),
                System.getProperty('twitter.oauth.secretToken')
        twitter.contentType = ContentType.JSON
        HttpConnectionParams.setSoTimeout twitter.client.params, 15000
    }

    @Test
    public void testConstructors() {
        twitter = new RESTClient()
        assert twitter.contentType == ContentType.ANY

        twitter = new RESTClient('http://www.google.com', ContentType.XML)
        assert twitter.contentType == ContentType.XML
    }

    @Test
    public void testHead() {
        try { // twitter sends a 302 Found to /statuses, which then returns a 406...  What??
            twitter.head path: 'asdf'
            assert false: 'Expected exception'
        }
        // test the exception class:
        catch (ex) {
            assert ex.response.status == 404
        }

        assert twitter.head(path: 'home_timeline.json').status == 200
    }

    @Test
    public void testGet() {
        // testing w/ content-type other than default:
        /* Note also that Twitter doesn't really care about the "Accept" header
           anyway, it wants you to put it in the URL, i.e. something.xml or
           something.json.  But we're still passing the content-type so that
           the parser knows how it should _attempt_ to parse the response.  */
        def resp = twitter.get(path: 'home_timeline.json')
        assert resp.status == 200
        assert resp.headers.Server == "tsa_b"
        assert resp.headers.Server == resp.headers['Server'].value
        assert resp.contentType == JSON.toString()
        assert (resp.data instanceof List)
//        assert resp.data.status.size() > 0 this condition does not make sense, You need to make a tweet before
    }

    @Test
    public void testPost() {
        def msg = "RESTClient unit test was run on ${new Date()}"

        def resp = twitter.post(
                path: 'update.json',
                body: [status: msg, source: 'httpbuilder'],
                requestContentType: URLENC)

        assert resp.status == 200
        assert resp.headers.Status
        assert resp.data.text == msg
        assert resp.data.user.screen_name == userID

        RESTClientTest.postID = resp.data.id
        println "Updated post; ID: ${postID}"
    }

    @Test
    public void testDelete() {
        Thread.sleep 10000
        // delete the test message.
        if (!postID) throw new IllegalStateException("No post ID from testPost()")
        println "Deleting post ID : $postID"
        def resp = twitter.post(path: "destroy/${postID}.json")
        assert resp.status == 200
        assert resp.data.id == postID
        println "Test tweet ID ${resp.data.id} was deleted."
    }

    @Ignore
    @Test
    public void testOptions() {
        // get a message ID then test which ways I can delete it:
        def resp = twitter.get(uri: 'http://twitter.com/statuses/user_timeline/httpbuilder.json')

        def id = resp.data[0].id
        assert id

        // This does not seem to be supported by the Twitter API..
/*      resp = twitter.options( path : "destroy/${id}.json" )
        println "OPTIONS response : ${resp.headers.Allow}"
        assert resp.headers.Allow
        */
    }

    @Test
    public void testDefaultHandlers() {
        def resp = twitter.get(path: 'user_timeline.json',
                query: [screen_name: 'httpbuilder', count: 2])
        assert resp.data.size() == 2

        try {
            resp = twitter.get([:])
            assert false: "exception should be thrown"
        }
        catch (HttpResponseException ex) {
            assert ex.response.status == 404
        }
    }

    @Test
    public void testQueryParameters() {
        twitter.contentType = 'text/javascript'
        twitter.headers = null
        def resp = twitter.get(
                path: 'user_timeline.json',
                queryString: 'count=5&trim_user=1',
                query: [screen_name: 'httpbuilder'])
        assert resp.data.size() == 5
    }

    @Test
    public void testUnknownNamedParams() {
        try {
            twitter.get(Path: 'user_timeline.json',
                    query: [screen_name: 'httpbuilder', count: 2])
            assert false: "exception should be thrown"
        }
        catch (IllegalArgumentException ex) { /* Expected exception */
        }
    }

    @Test
    public void testJSONPost() {
        def http = new RESTClient("http://restmirror.appspot.com/")
        def resp = http.post(
                path: '/', contentType: 'text/javascript',
                body: [name: 'bob', title: 'construction worker'])

        println "JSON POST Success: ${resp.statusLine}"
        assert resp.data instanceof Map
        assert resp.data.name == 'bob'
    }

    @Test
    public void testXMLPost() {
        def http = new RESTClient("http://restmirror.appspot.com/")

        def postBody = {
            person(name: 'bob', title: 'builder')
        }

        def resp = http.post(path: '/', contentType: XML, body: postBody)

        println "XML POST Success: ${resp.statusLine}"
        assert resp.data instanceof GPathResult
        assert resp.data.name() == 'person'
        assert resp.data.@name == 'bob'
        assert resp.data.@title == 'builder'
    }
}

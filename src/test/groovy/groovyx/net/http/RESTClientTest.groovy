package groovyx.net.http;

import spock.lang.*;
import junit.framework.Assert;
import groovy.util.slurpersupport.GPathResult;
import org.apache.http.params.HttpConnectionParams;
import static groovyx.net.http.ContentType.*;

class RESTClientTest extends Specification {

    def twitter = null
    static postID = null
    def userID = System.getProperty('twitter.user')

    def setup() {
        twitter = new RESTClient( 'https://api.twitter.com/1.1/statuses/' )
        twitter.auth.oauth(System.getProperty('twitter.oauth.consumerKey'),
                           System.getProperty('twitter.oauth.consumerSecret'),
                           System.getProperty('twitter.oauth.accessToken'),
                           System.getProperty('twitter.oauth.secretToken'))
        twitter.contentType = ContentType.JSON
        HttpConnectionParams.setSoTimeout twitter.client.params, 15000
    }

    def "Constructors"() {
        when:
        def twitter = new RESTClient();
        
        then:
        twitter.contentType == ContentType.ANY

        when:
        twitter = new RESTClient( 'http://www.google.com', ContentType.XML );

        then:
        twitter.contentType == ContentType.XML
    }

    @Ignore
    def "Head"() {
        try { // twitter sends a 302 Found to /statuses, which then returns a 406...  What??
            twitter.head path : 'asdf'
            assert false : 'Expected exception'
        }
        // test the exception class:
        catch( ex ) { assert ex.response.status == 404 }

        assert twitter.head( path : 'home_timeline.json' ).status == 200
    }

    @Ignore
    def "Get"() {
        // testing w/ content-type other than default:
        /* Note also that Twitter doesn't really care about the "Accept" header
           anyway, it wants you to put it in the URL, i.e. something.xml or
           something.json.  But we're still passing the content-type so that
           the parser knows how it should _attempt_ to parse the response.  */
        def resp = twitter.get( path : 'home_timeline.json' )
        assert resp.status == 200
        assert resp.headers.Server == "tfe"
        assert resp.headers.Server == resp.headers['Server'].value
        assert resp.contentType == JSON.toString()
        assert ( resp.data instanceof List )
        assert resp.data.status.size() > 0
    }

    @Ignore
    def "Post"() {
        def msg = "RESTClient unit test was run on ${new Date()}"

        def resp = twitter.post(
                path : 'update.json',
                body : [ status:msg, source:'httpbuilder' ],
                requestContentType : URLENC )

        assert resp.status == 200
        assert resp.headers.Status
        assert resp.data.text == msg
        assert resp.data.user.screen_name == userID

        RESTClientTest.postID = resp.data.id
        println "Updated post; ID: ${postID}"
    }

    @Ignore
    def "Delete"() {
        Thread.sleep 10000
        // delete the test message.
        if ( ! postID ) throw new IllegalStateException( "No post ID from testPost()" )
        println "Deleting post ID : $postID"
        def resp = twitter.delete( path : "destroy/${postID}.json" )
        assert resp.status == 200
        assert resp.data.id == postID
        println "Test tweet ID ${resp.data.id} was deleted."
    }

    @Ignore
    def "Options"() {
        // get a message ID then test which ways I can delete it:
        def resp = twitter.get( uri: 'http://twitter.com/statuses/user_timeline/httpbuilder.json' )

        def id = resp.data[0].id
        assert id

        // This does not seem to be supported by the Twitter API..
/*      resp = twitter.options( path : "destroy/${id}.json" )
        println "OPTIONS response : ${resp.headers.Allow}"
        assert resp.headers.Allow
        */
    }

    @Ignore
    def "Defaul tHandlers"() {
        def resp = twitter.get( path : 'user_timeline.json',
            query : [screen_name :'httpbuilder',count:2] )
        assert resp.data.size() == 2

        try {
            resp = twitter.get([:])
            assert false : "exception should be thrown"
        }
        catch ( HttpResponseException ex ) {
            assert ex.response.status == 404
        }
    }

    @Ignore
    def "Query Parameters"() {
        twitter.contentType = 'text/javascript'
        twitter.headers = null
        def resp = twitter.get(
            path : 'user_timeline.json',
            queryString : 'count=5&trim_user=1',
            query : [screen_name :'httpbuilder'] )
        assert resp.data.size() == 5
    }

    def "Unknown Named Params"() {
        when:
        twitter.get(Path: 'user_timeline.json',
                    query: [screen_name :'httpbuilder',count:2]);

        then:
        thrown(IllegalArgumentException);
    }

    def "JSON Post"() {
        setup:
        def http = new RESTClient("http://restmirror.appspot.com/");

        when:
        def resp = http.post(path:'/', contentType:'text/javascript',
                             body: [name: 'bob', title: 'construction worker']);

        then:
        resp.data instanceof Map
        resp.data.name == 'bob'
    }

    def "XML Post"() {
        setup:
        def http = new RESTClient("http://restmirror.appspot.com/")
        def postBody = {
            person( name: 'bob', title: 'builder' )
        }

        when:
        def resp = http.post(path:'/', contentType: XML, body: postBody);

        then:
        resp.data instanceof GPathResult
        resp.data.name() == 'person'
        resp.data.@name == 'bob'
        resp.data.@title == 'builder'
    }
}

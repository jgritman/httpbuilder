package groovyx.net.http

import org.junit.Ignore
import org.junit.Test

import org.apache.http.client.HttpResponseException
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

class HttpURLClientTest {

    def twitter = [ user: System.getProperty('twitter.user'),
                    consumerKey: System.getProperty('twitter.oauth.consumerKey'),
                    consumerSecret: System.getProperty('twitter.oauth.consumerSecret'),
                    accessToken: System.getProperty('twitter.oauth.accessToken'),
                    secretToken: System.getProperty('twitter.oauth.secretToken') ]

    /**
     * This method will parse the content based on the response content-type
     */
    @Test public void testGET() {
        def http = new HttpURLClient(url:'http://www.google.com')
        def resp = http.request( path:'/search', query:[q:'HTTPBuilder'],
                headers:['User-Agent':'Firefox'] )

        println "response status: ${resp.statusLine}"

        def html = resp.data
        assert html
        assert html.HEAD.size() == 1
        assert html.HEAD.TITLE.size() == 1
        println "Title: ${html.HEAD.TITLE.text()}"
        assert html.BODY.size() == 1
    }

    @Test public void testRedirect() {
        def http = new HttpURLClient(followRedirects:false)

        def params = [ url:'https://www.google.com/search',
                        query:[q:'HTTPBuilder', btnI:"I'm Feeling Lucky"],
                        headers:['User-Agent':'Firefox'] ]
        def resp = http.request( params )

        assert resp.statusLine.statusCode == 302
        assert ! http.followRedirects

        http.followRedirects = true
        resp = http.request( params )
        assert resp.statusLine.statusCode == 200
        assert resp.success
        assert resp.data
    }

    @Test public void testSetHeaders() {
        def http = new HttpURLClient(url:'http://www.google.com')
        def val = '1'
        def v2 = 'two'
        def h3 = 'three'
        def h4 = 'four'
        http.headers = [one:"v$val", "$v2" : 2]
        http.headers.three = 'not Three'
        http.headers."$h3" = 'three'

//      def resp = http.request( headers:[one:'v1',two:'2',three:'three',"$h4":"$val"] )
        // private member access to verify the request headers...
/*      def headers = resp.@responseBase.@conn.requestProperties

        assert headers.find { it.key == 'one' && it.value[0] == 'v1' }
        assert headers.find { it.key == 'two' && it.value[0] == '2' }
        assert headers.find { it.key == 'three' && it.value[0] == 'three' }
        assert headers.find { it.key == 'four' && it.value[0] == '1' }
*/  }


    @Test public void testFailure() {
        def http = new HttpURLClient(url:'http://www.google.com')

        try {
            def resp = http.request( path:'/adsasf/kjsslkd' )
        }
        catch( HttpResponseException ex ) {
            assert ex.statusCode == 404
            assert ! ex.response.success
            assert ex.response.headers.every { it.name && it.value }
        }
        assert http.url.toString() == 'http://www.google.com'
    }

    /**
     * This method is similar to testGET, but it will will parse the content
     * based on the given content-type, i.e. TEXT (text/plain).
     */
    @Test public void testReader() {
        def http = new HttpURLClient()
        def resp = http.request( url:'http://validator.w3.org/about.html',
                  contentType: TEXT, headers: [Accept:'*/*'] )

        println "response status: ${resp.statusLine}"

        assert resp.data instanceof StringReader

        // we'll validate the reader by passing it to an XmlSlurper manually.
        def resolver = ParserRegistry.catalogResolver
        def parsedData = new XmlSlurper( entityResolver : resolver ).parse(resp.data)
        resp.data.close()
        assert parsedData.children().size() > 0
    }

    /** W3C pages will have a doctype, but will return a 503 if you do a GET
     * for them with the Java User-Agent.
     */
    @Test public void testCatalog() {
        def http = new HttpURLClient(
                url:'http://validator.w3.org/',
                contentType: XML )

        def resp = http.request( path : 'about.html' )
        assert resp.data
    }

    /* REST testing with Twitter!
     * Tests POST with XML response, and DELETE with a JSON response.
     */

    @Test public void testPOST() {
        def http = new HttpURLClient(url:'https://api.twitter.com/1.1/statuses/')

        http.setOAuth twitter.consumerKey, twitter.consumerSecret,
                twitter.accessToken, twitter.secretToken

        def msg = "HTTPBuilder unit test was run on ${new Date()}"

        def resp = http.request( method:POST, contentType:JSON,
                path:'update.json', timeout: 30000,
                requestContentType:URLENC,
                body:[status:msg,source:'httpbuilder'] )

        println "Tweet response status: ${resp.statusLine}"
        assert resp.statusLine.statusCode == 200
        def json = resp.data

        assert json.text == msg
        assert json.user.screen_name == twitter.user
        def postID = json.id

        // delete the test message.
        resp = http.request( method:DELETE, contentType:JSON,
            path : "destroy/${postID}.json" )

        json = resp.data
        assert json.id != null
        assert resp.statusLine.statusCode == 200
        println "Test tweet ID ${json.id} was deleted."
    }

//  @Test
    public void testHeadMethod() {
        def http = new HttpURLClient(url:'http://api.twitter.com/1/statuses/')

        assert http.url.toString() == "http://api.twitter.com/1/statuses/"

        http.setOAuth twitter.consumerKey, twitter.consumerSecret,
                twitter.accessToken, twitter.secretToken

        def resp = http.request( method:HEAD, contentType:"application/xml",
                path : 'friends_timeline.xml' )

        assert resp.headers.Status == "200 OK"
    }

    @Test public void testParsers() {
        def parsers = new ParserRegistry()
        def done = false
        parsers.'application/json' = { done = true }

        def http = new HttpURLClient(
                url:'https://api.twitter.com/1.1/statuses/',
                parsers : parsers )

        http.setOAuth twitter.consumerKey, twitter.consumerSecret,
                twitter.accessToken, twitter.secretToken

        def resp = http.request( contentType: JSON, path : 'home_timeline.json' )
        assert done
        assert resp.data

        done = false
        parsers.defaultParser = { done = true }
        // remove content-type-specific parser to force use of default parser.
        parsers.'application/json' = null
        resp = http.request( contentType: JSON, path : 'home_timeline.json' )
        assert done
        assert resp.data
    }

    /* http://googlesystem.blogspot.com/2008/04/google-search-rest-api.html
     * http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=Earth%20Day
     */
    @Ignore
    @Test public void testJSON() {

        def http = new HttpURLClient()

        def resp = http.request( url:'http://ajax.googleapis.com',
                method:GET, contentType:JSON ,
            path : '/ajax/services/search/web',
            query : [ v:'1.0', q: 'Calvin and Hobbes' ],
            //UA header required to get Google to GZIP response:
            headers:['User-Agent' : "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.0.4) Gecko/2008111319 Ubuntu/8.10 (intrepid) Firefox/3.0.4"] )

//      assert resp.entity.contentEncoding.value == 'gzip'
        def json = resp.data
        assert json.size() == 3
//      println json.responseData
        println "Query response: "
        json.responseData.results.each {
            println "  ${it.titleNoFormatting} : ${it.visibleUrl}"
        }
    }

    @Test public void testUnknownNamedParam() {
        def http = new HttpURLClient()

        try {
            def resp = http.request( url:'http://ajax.googleapis.com',
                    method:GET, contentType:JSON ,
                Path : '/ajax/services/search/web',
                query : [ v:'1.0', q: 'Calvin and Hobbes' ] )
            assert false : "Unknown argument should have thrown exception"
        }
        catch ( IllegalArgumentException ex ) { /* Expected exception */ }
    }

    @Test(expected = SocketTimeoutException)
    void testTimeout() {
        new HttpURLClient(url: 'https://www.google.com/').request(timeout: 1)
    }
}

package groovyx.net.http;

import spock.lang.*;

class URIBuilderTest extends Specification {

    def "Constructors"() {
        setup:
        def string = 'http://google.com/search/q?one=1';
        
        when:
        def uri = new URIBuilder(null);

        then:
        thrown(Exception);

        when:
        uri = new URIBuilder(new URI(string));

        then:
        uri.toString() == string;

        when:
        uri = new URIBuilder(new URL(string));

        then:
        uri.toString() == string

        when:
        uri = new URIBuilder(string);

        then:
        uri.toString() == string

        when:
        def uri2 = URIBuilder.convertToURI(uri)

        then:
        uri2 == uri.toURI()
    }

    def "Clone And Equals"() {
        setup:
        def string = 'http://google.com/search/q?one=1'
        def uri = new URIBuilder(string)
        def u2 = uri.clone()

        expect:
        u2 == uri
        u2.toString() == uri.toString()
    }

    def "Typecast"() {
        setup:
        def string = 'http://google.com/search/q?one=1'

        when:
        def uri = new URIBuilder( string )
        def u2 = uri as URI

        then:
        u2 instanceof URI;
        u2.toString() == uri.toString();

        when:
        u2 = uri as URL

        then:
        u2 instanceof URL;
        u2.toString() == uri.toString();

        when:
        u2 = uri as String

        then:
        u2 instanceof String;
        u2 == uri.toString();

        when:
        u2 = new URIBuilder( new URI(string) );

        then:
        u2 == uri;
        u2.toString() == uri.toString();

        when:
        u2 = new URIBuilder(new URL(string));

        then:
        u2 == uri
        u2.toString() == uri.toString()
    }

    def "Path"() {
        setup:
        def uri = new URIBuilder('http://localhost/p1/p2?a=1&b=2&c=3#frag');

        when:
        uri.path = 'p2a';
        
        then:
        uri.toString() == 'http://localhost/p1/p2a?a=1&b=2&c=3#frag';

        when:
        uri.path = 'p2/p3/';

        then:
        uri.toString() == 'http://localhost/p1/p2/p3/?a=1&b=2&c=3#frag';

        when:
        uri.path = 'p4';

        then:
        uri.toString() == 'http://localhost/p1/p2/p3/p4?a=1&b=2&c=3#frag';

        when:
        uri.path = '../../p2b';

        then:
        uri.toString() == 'http://localhost/p1/p2b?a=1&b=2&c=3#frag';

        when:
        def p = 'p5'
        uri.path = "/p4/$p"

        then:
        uri.toString() == 'http://localhost/p4/p5?a=1&b=2&c=3#frag'
    }

    def "Path Escaping"() {
        setup:
        def uri = new URIBuilder( 'http://localhost/' );

        when:
        uri.path = "/p1 p2";

        then:
        uri.toString() == 'http://localhost/p1%20p2';

        when:
        uri.fragment = 'fr#ag'

        then:
        uri.toString() == 'http://localhost/p1%20p2#fr%23ag';

        when:
        uri = new URIBuilder('http://localhost/p1?one=1#frag');
        uri.path = "/p1 p2 p3";

        then:
        uri.toString() == 'http://localhost/p1%20p2%20p3?one=1#frag';
    }

    // When params are added to a path, does it goober up the escaped-then-unescaped path?
    def "Path Escaping 2"() {
        setup:
        def uri = new URIBuilder('http://johannburkard.de/%22bla%22');
        uri.query = [ what_is_this: 'i_dont_even' ];

        when:
        uri = new URIBuilder( 'http://codehaus.org/')
        uri.path = '"bla"'
        uri.fragment = 'what evs'

        then:
        uri.toString() == 'http://codehaus.org/%22bla%22#what%20evs';

        when:
        uri.query = [ a: 'b#' ];

        then:
        uri.toString() == 'http://codehaus.org/%22bla%22?a=b%23#what%20evs';

        when:
        uri.port = 80;
        uri.host = 'google.com';
        uri.scheme = 'https';

        then:
        uri.toString() == 'https://google.com:80/%22bla%22?a=b%23#what%20evs';
    }

    def "Params"() {
        when:
        def uri = new URIBuilder( 'http://localhost/p1/p2?a=1&b=2&c=3#frag' );

        then:
        uri.query.size() == 3
        uri.query.a == '1'
        uri.query.b == '2'
        uri.query.c == '3'

        when:
        uri.addQueryParam 'd', '4'
        
        then:
        uri.query.d == '4'
        assert uri.hasQueryParam("d");

        when:
        uri.removeQueryParam "b";

        then:
        !uri.hasQueryParam("b");
        uri.query.b == null

        when:
        uri.addQueryParam 'b', ''
        uri.addQueryParams( [ e : 5, f : 6, a : 7 ] )
        def query = uri.query

        then:
        query.containsKey( 'b' );
        query.size() == 6;
        query == [a:['1','7'], c:'3', d:'4', b:'', e:'5', f:'6'];

        when:
        uri.query = [z:0,y:9,x:8];

        then:
        uri.toString() == 'http://localhost/p1/p2?z=0&y=9&x=8#frag';

        when:
        uri.addQueryParams( z:1, y:2 );

        then:
        uri.toString() == 'http://localhost/p1/p2?z=0&y=9&x=8&z=1&y=2#frag';

        when:
        uri.addQueryParam('y', 'blah');

        then:
        uri.toString() == 'http://localhost/p1/p2?z=0&y=9&x=8&z=1&y=2&y=blah#frag';

        when:
        uri.query = null

        then:
        uri.toString() == 'http://localhost/p1/p2#frag';

        when:
        uri.query = [:];

        then:
        uri.toString() == 'http://localhost/p1/p2#frag';

        when:
        uri = new URIBuilder( 'http://localhost/p1/p2' );
        uri.addQueryParam('z','1');

        then:
        uri.query.z == '1';
    }

    def "Most Everything Else"() {
        setup:
        def url = 'http://localhost:80/one/two?a=%261#frag'
        def uri = new URIBuilder(url)

        when:
        uri.fragment = 'asdf#2';
        then:
        uri.toString() == 'http://localhost:80/one/two?a=%261#asdf%232';

        when:
        uri.path = '/one two';
        then:
        uri.toString() == 'http://localhost:80/one%20two?a=%261#asdf%232';

        when:
        uri.scheme = "https";
        then:
        uri.toString() == 'https://localhost:80/one%20two?a=%261#asdf%232';

        when:
        uri.port = 8080;
        then:
        uri.toString() == 'https://localhost:8080/one%20two?a=%261#asdf%232';

        when:
        uri.host = 'google.com';
        then:
        uri.toString() == 'https://google.com:8080/one%20two?a=%261#asdf%232';

        when:
        uri.userInfo = 'billy';
        then:
        uri.toString() == 'https://billy@google.com:8080/one%20two?a=%261#asdf%232';
    }

    def "Param Encoding"() {
        setup:
        def uri = new URIBuilder( 'http://localhost:8080#test' )

        when:
        uri.query = [q:'a:b',z:'y&z'];
        then:
        'http://localhost:8080?q=a%3Ab&z=y%26z#test' == uri.toString()

        when:
        uri.scheme = 'ftp';
        then:
        'ftp://localhost:8080?q=a%3Ab&z=y%26z#test' == uri.toString()

        when:
        uri.path = '/one';
        then:
        'ftp://localhost:8080/one?q=a%3Ab&z=y%26z#test' == uri.toString();

        when:
        uri.query = ['a&b':'c+d=e'];
        then:
        "ftp://localhost:8080/one?a%26b=c%2Bd%3De#test" == uri.toString();

        when:
        uri.query = [q:"war & peace"];
        then:
        "ftp://localhost:8080/one?q=war+%26+peace#test" == uri.toString();

        when:
        uri.host = 'google.com';
        then:
        "ftp://google.com:8080/one?q=war+%26+peace#test" == uri.toString();

        when:
        uri.port = 29;
        then:
        "ftp://google.com:29/one?q=war+%26+peace#test" == uri.toString();

        when:
        uri.fragment = "hi";
        then:
        "ftp://google.com:29/one?q=war+%26+peace#hi" == uri.toString();
    }

    def "Properties"() {
        // getQuery is already covered;
        setup:
        def uri = new URIBuilder('http://localhost/')

        when:
        uri.with {
            scheme = 'https';
            userInfo = 'bill';
            host = 'www.google.com';
            port = 88;
            path = 'test';
            fragment = 'blah';
        }

        then:
        uri.toString() == 'https://bill@www.google.com:88/test#blah'
        uri.scheme == 'https'
        uri.userInfo == 'bill'
        uri.host == 'www.google.com'
        uri.port == 88
        uri.path == '/test'
        uri.fragment == 'blah'
    }

    def "Raw Query"() {
        setup:
        def uri = new URIBuilder('http://localhost:80/');

        when:
        uri.rawQuery = '1%20AND%202';

        then:
        uri.toString() == 'http://localhost:80/?1%20AND%202';
        uri.toURI().rawQuery == '1%20AND%202';

        when:
        uri.path = 'some/path'

        then:
        uri.toString() == 'http://localhost:80/some/path?1%20AND%202';

        when:
        uri.fragment = 'asdf'
        
        then:
        uri.toString() == 'http://localhost:80/some/path?1%20AND%202#asdf';

        when:
        uri.rawQuery = "a%20b=c%31d";

        then:
        uri.toString() == 'http://localhost:80/some/path?a%20b=c%31d#asdf';
    }
}

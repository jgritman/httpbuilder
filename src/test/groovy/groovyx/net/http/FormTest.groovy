package groovyx.net.http;

import spock.lang.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static groovyx.net.http.Form.*;

class FormTest extends Specification {

    def "Back and Forth"() {
        setup:
        def one = [ foo: ['bar'], baz: ['floppy'] ];
        def two = [ 'key&&=': [ 'one', 'two', 'three' ],
                    key2: [ 'one&&', 'two&&', 'three&&' ] ];

        expect:
        one == decode(new ByteArrayInputStream(encode(one, UTF_8).getBytes(UTF_8)), UTF_8);
        two == decode(new ByteArrayInputStream(encode(two, UTF_8).getBytes(UTF_8)), UTF_8);
    }

    def "Encoding"() {
        setup:
        def map = [ custname: 'bar&&', custtel: '111==', custemail: [],
                    delivery: [], comments: null ];
        def shouldBe = 'custname=bar%26%26&custtel=111%3D%3D&custemail=&delivery=&comments='

        expect:
        shouldBe == encode(map, UTF_8);
    }

    def "Round Trip All Types"() {
        setup:
        def map = [ custname: 'bar&&', custtel: '111==', custemail: [],
                    delivery: '', comments: null ];
        def shouldBe =  [ custname: ['bar&&'], custtel: ['111=='], custemail: [],
                          delivery: [], comments: [] ];

        def returned = decode(new ByteArrayInputStream(encode(map, UTF_8).getBytes(UTF_8)), UTF_8);

        expect:
        returned == shouldBe;
    }
}

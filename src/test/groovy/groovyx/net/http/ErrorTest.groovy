import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.junit.WireMockRule
import groovyx.net.http.RESTClient

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import org.junit.Rule
import org.junit.Test

class ErrorTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule()

    @Test
    void firstTest() {
        assert true
    }

    @Test
    void wireMockFault() {
        wireMockRule.stubFor(get(urlEqualTo("/fault"))
            .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE))
        )

        RESTClient restClient = new RESTClient("http://localhost:8080")
        def response = restClient.get(path: "/fault")

        assert true
    }
}

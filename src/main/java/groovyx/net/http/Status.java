/*
 * Copyright 2008-2011 Thomas Nichols.  http://blog.thomnichols.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You are receiving this code free of charge, which represents many hours of
 * effort from other individuals and corporations.  As a responsible member
 * of the community, you are encouraged (but not required) to donate any
 * enhancements or improvements back to the community under a similar open
 * source license.  Thank you. -TMN
 */
package groovyx.net.http;

/**
 * Mapping of HTTP response codes to a constant 'success' or 'failure' value.
 *
 * @author <a href='mailto:tomstrummer+httpbuilder@gmail.com'>Tom Nichols</a>
 */
public enum Status {
    /**
     * Any status code &gt;= 100 and &lt; 400
     */
    SUCCESS(100, 399),
    /**
     * Any status code &gt;= 400 and &lt; 1000
     */
    FAILURE(400, 999);

    private final int min, max;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    /**
     * Returns true if the numeric code matches the represented status (either
     * <code>success</code> or <code>failure</code>).  i.e.
     * <pre>
     * assert Status.SUCCESS.matches(200);
     * assert Status.FAILURE.matches(404);
     * </pre>
     *
     * @param code numeric HTTP code
     * @return true if the numeric code represents this enums success or failure
     * condition
     */
    public boolean matches(int code) {
        return min <= code && code <= max;
    }

    /**
     * Find the Status value that matches the given status code.
     *
     * @param code HTTP response code
     * @return a 'success' or 'failure' Status value
     * @throws IllegalArgumentException if the given code is not a valid HTTP
     *                                  status code.
     */
    public static Status find(int code) {
        for (Status s : Status.values())
            if (s.matches(code)) return s;
        throw new IllegalArgumentException("Unknown status: " + code);
    }

    private Status(int min, int max) {
        this.min = min;
        this.max = max;
    }
}

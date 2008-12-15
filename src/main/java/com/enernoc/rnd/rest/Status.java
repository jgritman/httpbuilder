/*
 * Copyright 2003-2008 the original author or authors.
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
 * of the community, you are asked (but not required) to donate any 
 * enhancements or improvements back to the community under a similar open 
 * source license.  Thank you. -TMN
 */
package com.enernoc.rnd.rest;

public enum Status {
	SUCCESS ( 100, 399 ),		
	FAILURE ( 400, 999 );

	private final int min, max;
	
	@Override public String toString() {
		return super.toString().toLowerCase();
	}
	
	public boolean matches( int code ) {
		return min <= code && code <= max;
	}
	
	public static Status find( int code ) {
		for ( Status s : Status.values() ) 
			if ( s.matches( code ) ) return s;
		throw new IllegalArgumentException( "Unknown status: " + code );
	}
	
	private Status( int min, int max ) {
		this.min = min; this.max = max;
	}
}
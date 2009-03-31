/**
 * 
 */
package groovyx.net.http;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts keys to strings, mainly to normalize the difference between 
 * GString and String keys since a GString will not produce the same hashcode as
 * its equivalent string.
 * 
 * Basically, any given key will always be coerced to a String, and any retrieved 
 * key (either via {@link #keySet()} or {@link #entrySet()} will always be a
 * String. 
 * @author tnichols@enernoc.com
 */
class StringHashMap<V> extends HashMap<Object,V> {
	
	private static final long serialVersionUID = -92935672093270924L;

	public StringHashMap() { super(); }
	
	public StringHashMap( Map<?,? extends V> contents ) {
		super();
		this.putAll( contents );
	}
	
	@Override
	public boolean containsKey( Object key ) {
		if ( key == null ) return false;
		return super.containsKey( key.toString() );
	}
	
	@Override
	public V get( Object key ) {
		if ( key == null ) return null;
		return super.get( key.toString() );
	}
	
	public V put(Object key, V value) {
		return key != null ? super.put( key.toString(), value ) : value; 
	}
	
	@Override
	public void putAll( Map<?, ? extends V> m ) {
		for ( Object key : m.keySet() ) this.put(  key, m.get( key ) );
	}
	
	@Override
	public V remove( Object key ) {
		if ( key == null ) return null;
		return super.remove( key.toString() );
	}
}

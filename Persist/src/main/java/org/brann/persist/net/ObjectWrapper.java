/**
 * 
 */
package org.brann.persist.net;

import java.io.Serializable;

/**
 * @author john
 *
 */
@SuppressWarnings("serial")
class ObjectWrapper implements Serializable {
	
	String SourceClassName;
	byte[] value;
	
	ObjectWrapper (String srcClass, byte[] val) {
		SourceClassName = srcClass;
		value = val;
	}

	public String getSourceClassName() {
		return SourceClassName;
	}

	public byte[] getValue() {
		return value;
	}

}

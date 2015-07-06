/*
 * Copyright (c) 2003 John Brann.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *      This product includes software developed by John Brann.
 * 4. John Brann's name may not be used to endorse or promote products 
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY JOHN BRANN``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL JOHN BRANN BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package org.brann.persist;

import java.io.Serializable;

@SuppressWarnings("serial")
class PersistentLogEntry extends LogEntry {
    /**
     * Builds a LogEntry representing a Persistent object update. 
     */
    PersistentLogEntry(Persistent p, Transaction tx) {
        
        super(tx);
	name = p.getName();
        
        try {
            pdiClass  = p.getPersistentClass();
            value = p.getCommittingValue(tx);
        } catch (PersistException pe) { /* don't care */  }
    }

    public PersistentLogEntry() {
    }
    
    /**
     * getter for the name of the Persistent object in this LogEntry.  If this LogEntry is an 
     * end-of-transaction marker, returns the 'COMMIT' or 'ROLLBACK' marker. 
     */
    String getName() {
    
	return name;
    }

    /**
     * Returns the content of the Persistent object or transaction end marker contained in this LogEntry.  
     * returns null if the entry represents an end-of-transaction marker. 
     */
    Serializable getVal() {
        return value;
    }

    /**
     * Returns the class of the Persistent Object in the LogEntry.  Returns null for an end-of-transaction marker. 
     */
    Class<?> getPersistentClass() {
	return pdiClass;
    }

    /**
     * returns true if the content is null.
     */
    boolean isNull() {
        return (value == null);
    }

    protected String name;
    protected Serializable value;
    protected Class<?> pdiClass;

    /** @link dependency */
    /*#Persistent lnkPersistent;*/
}

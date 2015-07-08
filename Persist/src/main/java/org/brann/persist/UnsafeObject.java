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
/*
 * SafeToClone.java
 *
 * Created on May 8, 2003, 4:53 PM
 */

package org.brann.persist;

/**
 * This Interface can be used by clients on the classes they wish to manage persistently.
 * It indicates that an accurate (deep) copy of an object of this class WILL NOT be made for
 * these objects.
 * 
 * When this interface is referenced on the class of an object being made persistent, an 
 * object reference will be kept, rather than making a deep copy (by serialization/deserialization)
 *
 * This means that the integrity of the object contents are not guaranteed - the object state at 
 * TransactionHandle.commit() time may have been changed since the last TransactionHandle.setPstValue().
 * TransactionHandle.rollback() is not effective.
 * 
 * This technique is useful for immutable objects that an application guarantees are not changed after SetPstValue().
 * an example might be a log record.
 * 
 * Note that UnsafeObjects must still be serializable so they can be stored and recovered.
 * 
 * This provides extremely high performance, with the loss of all of the transaction guarantees of the Persistence system.
 * 
 * @author  jbrann
 */
public interface UnsafeObject extends java.io.Serializable {
    
}

/*
 * Copyright (c) 2000 John Brann.  All rights reserved.
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

import java.io.*;

/**
 * Represents a client's connection to the persistence engine.  Created by the 
 * factory method 'getTransactionHandle()'.  
 * TransactionHandles are long-lived objects representing 
 * A client can use as many TransactionHandles
 * as it chooses.  In general it is normal that TransactionHandles are not shared by 
 * multiple threads, however, there should be no problem in doing so.
 * Provides the client API for obtaining persisted values and committing or rolling back
 * transactions. 
 */
public class TransactionHandle {
    /**
     * Private default constructor.  Note that clients get TransactionHandle objects through the 
     * getTransactionHandle() factory method. 
     */
    private TransactionHandle() {
    }

     /**
      * returns the transaction associated with this TransactionHandle.  If there is none when called, obtains a new transaction from the TransactionMgr and makes it current. 
      */
     Transaction getTransaction() {
        if (transaction == null) {
            transaction = tManager.newTransaction(this);
        }
        return transaction;
    }

	/**
	  * Returns the arbitrary, unique ID of the current transaction.  (Debugging use only) 
	  */
	 public long getID() {
	 	
	 	Transaction current = getTransaction();
	 	if (current != null) {
	 		return current.getID();
	 	} else {
	 		return -1L;
	 	}
	 }
    

    /**
     * undo all actions under the current transaction. 
     */
    public void rollBack() {

        if (transaction != null) {
            transaction.rollBack();
            transaction = null;
        }
    }

    /**
     * commit all actions done under the present transaction, making them visible to other clients. 
     */
    public void commit() throws PersistException {
        
        if (transaction != null) {
            Transaction myTr = transaction;
            transaction = null;
            myTr.commit();
        }
    }

    /**
     * Public factory method to provide a TransactionHandle to a requesting client.
     * If the TransactionMgr is not 'UP', returns null. 
     */
    public static TransactionHandle getTransactionHandle () {

	if (!tManager.isUp()) {
	    return null;
        } else {
            return new TransactionHandle();
        }
    }
    
    /**
     * Returns the content of the Persistent object specified by the name and class parameters.
     * If no such object exists, throws a PersistException.
     * If successful, the identified persistent object is controlled by the transaction owned
     * by this TransactionHandle.  No client using a different TransactionHandle will be able to
     * access the Persistent until either a commit() or rollBack() is executed, or the 
     * transaction is killed in deadlock resolution.
     * If a transaction owned by a different TransactionHandle currently controls the 
     * identified Persistent object, this method blocks until that transaction is committed, rolled back or killed. 
     */
    public Serializable getExistingPstValue(String name, Class persistClass) throws PersistException {
        Persistent tmp = tManager.getExistingPersistent(name, persistClass);
        
        if (tmp == null ) {
            throw new PersistException ("No such Object in Persistent Library");
        } else {
            return getPstValue(name, persistClass);
        }
    }
    
    /**
     * Returns the content of the Persistent object specified by the name and class parameters.
     * If no such object exists, a new one is created, with null content.
     * The identified persistent object is controlled by the transaction owned
     *  by this
     * TransactionHandle.  No client using a different TransactionHandle will be able to
     * access the Persistent until either a commit() or rollBack() is executed, or the 
     * transaction is killed in deadlock resolution.
     * If a transaction owned by a different TransactionHandle currently controls the 
     * identified Persistent object, this method blocks until that transaction is committed, rolled back or killed.
     */
    public Serializable getPstValue(String name, Class persistClass) throws PersistException {

        if ((getTransaction()) == null) {
            throw new PersistException ("Persistence Engine Down.");
        }
        
        try {
            return transaction.getPstValue(name, persistClass);
        } catch (PersistException pe) {
            transaction = null;
            throw pe;
        }
    }
    
    /**
     * Sets the content of the Persistent object specified by the name and class parameters 
     * to the value parameter.
     * If no such object exists, a new one is created.
     * The identified persistent object is controlled by the transaction owned by this
     * TransactionHandle.  No client using a different TransactionHandle will be able to
     * access the Persistent until either a commit() or rollBack() is executed, or the 
     * transaction is killed in deadlock resolution.
     * If a transaction owned by a different TransactionHandle currently controls the 
     * identified Persistent object, this method blocks until that transaction is committed, 
     * rolled back or killed. 
     */
    public void setPstValue(String name, Class persistClass, Serializable value) throws PersistException {
     
        if ((getTransaction()) == null) {
            throw new PersistException ("Persistence Engine Down.");
        }
        
        try {
            transaction.setPstValue(name, persistClass, value);
        } catch (PersistException pe) {
            transaction = null;
            throw pe;
        }
    }
    
    private Transaction transaction;
    private static TransactionMgr tManager = TransactionMgr.getInstance();
}

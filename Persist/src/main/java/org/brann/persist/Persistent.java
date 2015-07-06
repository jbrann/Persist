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

import java.io.Serializable;


/**
 * Shell object that contains the (Serializable) objects to be managed by the engine.  Each 
 * identified object is stored in a separate instance of Persistent.
 * Manages object content during transactions and implements a two-phase commit
 * protocol to provide consistent behaviour in all circumstances. 
 */
@SuppressWarnings("serial")
abstract class Persistent implements Serializable, java.lang.Comparable<Persistent> {
    
    static {
        try {
            cClass = Class.forName("org.brann.persist.SafeToClone");
            uClass = Class.forName("org.brann.persist.UnsafeObject");
        } catch (ClassNotFoundException cnfe) {
            cClass = null;
            uClass = null;
        }
    }
    /**
     * recreate a Persistent from a Transaction LogEntry.  Used during start-up to roll-forward the log.
     **/
    Persistent(PersistentLogEntry pdi) {
        valueCommitted = pdi.getVal();
        name = pdi.getName();
        persistentClass = pdi.getPersistentClass();
        init();
    }
    
    /**
     * Build a new empty Persistent
     */
    Persistent(String name, Class<?> type) {
        this.name = name;
        persistentClass = type;
        init();
    }
    
    /**
     * Build a new persistent from name, class and content.
     *
    Persistent (String name, Class type, byte[] value) {
        
        this(name, type);
        valueCommitted = value;
    }*/
    
    /**
     * Return the content of this Persistent object, in its native form. 
     */
    synchronized Serializable getValue(Transaction tx) 
                throws PersistException {
                    
        if (tx == null) {
            throw new PersistException ("Attempted get without transaction.");
        } else if (validateXaction(tx)) {
        
                if (!changedInTransaction) {
                    if (valueCommitted == null) {
                        valueTransaction = null;
                    } else {
                        valueTransaction = obtainValue(valueCommitted);
                    }
                return valueTransaction;
                }
            }
        return null;
    }

    protected abstract Serializable obtainValue(Serializable val) throws PersistException;
    protected abstract Serializable storeValue(Serializable val) throws PersistException;
    
    /**
     * return the identifying name of the Persistent object. 
     */
    public String getName() { 
        return name;
    }

    /**
     * returns the exact Class of the Persistent object content. 
     */
    public Class<?> getPersistentClass() {
        return persistentClass;
    }
    
    /**
     * sets the Persistent content to the parameter value, under the parameter transaction.  if the Persistent is currently under the control of a different transaction or the value parameter is not an object of the class specified for this Persistent,  throws a PersistException.
     * If the parameter transaction is not 'alive' - it is marked for forced roll-back, is committed or otherwise completed, no action is taken. 
     */
    synchronized void set (Transaction tx, Serializable value) throws PersistException {

        if (validateXaction(tx)) {
            if (value != null &&
                !(value.getClass().equals(persistentClass))) {

                throw new PersistException("Attempt to change class of Persistent");
            }
        
            changedInTransaction = true;
            valueTransaction = value;
        }
    }

     /**
      * returns true if the content of this Persistent has been changed (by set()) 
      * by the transaction that currently controls the object.
      * If no transaction controls the object, or no set() call has been made in 
      * the current transaction, returns false. 
      */
     boolean getChangePending() {
        return changedInTransaction;
    }

     /**
      * Undo all actions performed under the currently controlling transaction - 
      * returning the object to its state before the transaction started. 
      */
     void rollBack(Transaction tx) {

         if (current == tx) {
             clearXaction();
         }
    }


    public String toString() {
        
        Object val = null;
        try {
            val = obtainValue(valueCommitted);
        } catch (Exception e) {}
        
        if (val == null) {
            val = valueCommitted;
        }
        return ("PERSISTENT name: " + name +
//              "\n           class: " + persistentClass +
              "\n           Committed = " + val);
    }
		
    /** validate commit request and prepare for finalization of commit.
     * Returns true if this persistent is able to commit, false otherwise.
     */
    synchronized boolean commitPhase1(Transaction tx) {
        if (tx == current) {
            inCommit = true;
            if (changedInTransaction && valueTransaction != null) {
                try {
                    valueCommitting = storeValue(valueTransaction);
                } catch (Exception e) {
                    System.err.println("Failed serialization - aborting transaction: " + e);
                    return false;
                }
            } else {
                valueCommitting = null;
            }
            return true;
        }  else {
            return false;
        }
    }    

    /**
     * complete the commit action. 
     */
    synchronized void commitPhase2(Transaction tx) {
        
        if (changedInTransaction) {
            valueCommitted = valueCommitting;
        }
        clearXaction();
    }
    
    /**
     * If the argument transaction is current and commitPhase1 has been called, 
     * returns the raw, serialized content of the value of this Persistent that is  
     * being committed.  Otherwise, returns null.
     */
    Serializable getCommittingValue(Transaction tx) throws PersistException {
        // Only called when logging commit - otherwise returns null
        
        if (current == tx && inCommit) {
            return valueCommitting;
        } else {
            return null;
        }
    }
    
    /**
     * Dirty read that returns the raw, serialized committed content of the Persistent object. 
     *
    Object getValue() {
        return valueCommitted;
    }
    */
    /**
     * Tidy up all internal state and interim content.  returns the object to a state where it can 
     * give control to a transaction. 
     */
    private synchronized void clearXaction() {
        valueTransaction = null;
        valueCommitting = null;
        current = null;
        changedInTransaction = false;
        inCommit = false;
    }
    
    /**
     * Perform initialization that is necessary for both normal construction and restoration by 
     * de-serialization. 
     */
    private void init() {
        if (tManager == null) {
            tManager = TransactionMgr.getInstance();
        }
        changedInTransaction = false;
        inCommit = false;
    }
    
    /**
     * Overrides standard de-serialization to perform correct initialization of static 
     * and volatile content. 
     */
    private void readObject(java.io.ObjectInputStream in)
     throws java.io.IOException, ClassNotFoundException {
         in.defaultReadObject();
         init();
    }
 
    
    /** Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.<p>
     *
     * @param   o the Persistent to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     * 		is less than, equal to, or greater than the specified object.
     *
     */
    public int compareTo(Persistent o) {
        
        if (this.equals(o)) {
            return 0;
        } else {
            int nmCmp = name.compareTo(((Persistent)o).name);
            if (nmCmp == 0) {
                nmCmp = persistentClass.getName().compareTo(((Persistent)o).persistentClass.getName());
            }
            return nmCmp;
        }
    }
    
    /**
     * If available for control, make the parameter transaction the controlling transaction.  
     * Return the current controlling transaction. 
     */
    synchronized Transaction giveControl(Transaction acquirer) {
        
        setCurrent(acquirer);
        return current;
    }

    private void setCurrent(Transaction acquirer) {

        if (current == null) {
            current = acquirer;
        }
    }

    public Object getKey() {
        return (persistentClass.getName()+name);
    }
    
    public static Object makeKey(java.lang.String name, java.lang.Class<?> pClass) {
        return (pClass.getName()+name);
    }
    
    private boolean validateXaction(Transaction tx) throws PersistException {

        if (!tx.isAlive()) {
            // this transaction is being killed - inform it by throwing exception.
            throw new PersistException ("Transaction killed.");
        }
        
        if (current != tx) {
            throw new PersistException ("Attempt to access persistent by wrong transaction.");
        }
        return true;
    }
    
    static Persistent persistentFactory(PersistentLogEntry ple) {
        
        if (cClass != null &&
            cClass.isAssignableFrom(ple.getPersistentClass())) {
            return new ClonedPersistent(ple);
        } else if (uClass != null &&
                   uClass.isAssignableFrom(ple.getPersistentClass())) {
            return new UnsafePersistent(ple);
        } else {
            return new SerializedPersistent(ple);
        }
    }
    
    static Persistent persistentFactory(String name, Class<?> pClass) {
        
        if (cClass != null &&
            cClass.isAssignableFrom(pClass)) {
            return new ClonedPersistent(name, pClass);
        } else if (uClass != null &&
                   uClass.isAssignableFrom(pClass)) {
            return new UnsafePersistent(name, pClass);
        } else {
            return new SerializedPersistent(name, pClass);
        }
    }
    
    private static TransactionMgr tManager = null;
    private static Class<?> cClass;
    private static Class<?> uClass;

    private transient Transaction current;
    private transient boolean inCommit;
    private transient boolean changedInTransaction;
    protected transient Serializable valueCommitting;
    protected transient Serializable valueTransaction;
    
    private String name;
    private Class<?> persistentClass;
    protected Serializable valueCommitted;
}


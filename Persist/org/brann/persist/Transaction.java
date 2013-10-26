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

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Date;

/**
 * Control point for a set of actions on Persistent objects.  Allows those actions to be committed or rolled back as a unit. 
 */
class Transaction {
    /**
     * Creates a new Transaction for the argument TransactionHandle. 
     */
    Transaction(TransactionHandle requester) {
        persistents = new HashMap();
        toLibrary = new LinkedList();
        tm = TransactionMgr.getInstance();
        logger = tm.getLogger();
        recycle(requester);
    }
    
    /**
     * Returns the Date object representing the milli-second time of the creation of this transaction. 
     */
    Date getDate() {
        return created;
    }
    
    /**
     * Roll back this transaction, undo-ing all changes made under its control and freeing all
     * Persistent objects held by it.  Transactions that are blocked, waiting for control of those
     * Persistent objects will have a chance to proceed.
     * No further actions can be taken by the transaction after the completion of the roll-back. 
     */
    void rollBack() {
        Persistent wk;
        boolean mustRollBack = false;
    
        synchronized (this) {
            if (!committed &&
                !rolledBack) {
                rolledBack = true;

                logger.logRollBack(this);
                mustRollBack = true;
            }
        }
            
        if (mustRollBack) {
            for (Iterator i = persistents.values().iterator();
                 i.hasNext();) {
                
                ((Persistent)i.next()).rollBack(this);
            }
            synchronized (this) {
                notifyAll();
            }
            tm.removeXaction(this);
        }
    }
    
    /**
     * Implement two-phase commit protocol for persistent objects.
     * Phase 1 validates the transaction for each affected Persistent
     * and prepares for commit.  After a successful phase 1, log entries are 
     * written for all the modified Persistents and the commit, followed by phase 2 
     * which changes the value
     *  and removes the transaction from each of the Persistents.
     * A failed phase 1 triggers a roll-back.
     */
    
    void commit() throws PersistException {
        
        Iterator i;
        Persistent wk;
        LogEntry wkDI;
        toLibrary.clear();
        
        if (!isAlive()) {
            rollBack();
            throw new PersistException("Transaction Rolled back before commit");
        } else {
            long TimeStamp = System.currentTimeMillis();
            
            tm.startCommit();
            
            for (i = persistents.values().iterator();
                 i.hasNext();) {
                
                wk = (Persistent)i.next();
                wkDI = null;
                
                if (!(wk.commitPhase1(this)) ||
                    (wk.getChangePending() &&
                     ((wkDI = logger.logEntry(wk, this)) == null))) {
                    
                    rollBack();
                    tm.endCommit();
                    throw new PersistException("Commit failed in Phase 1");
                }
                
                if (wkDI != null) {
                    toLibrary.add(wkDI);
                }
            }
        }
        // Log commit - check that we haven't been killed while committing.
        if (!killed && logger.logCommit(this)) {
            
            committed = true;
            // complete out the updates
            for (i = persistents.values().iterator();
                 i.hasNext();) {
            
                wk = (Persistent)i.next();
                wk.commitPhase2(this);
            }
        
            // Tell the library which persistents were updated
            if (toLibrary.size() != 0) {
                tm.changesCommitted(toLibrary);
            }
            tm.endCommit();
        
            // remove blocks caused by this transaction
            // this may also trigger a checkPoint()
            tm.removeXaction(this);
        } else {
            rollBack();
            tm.endCommit();
            throw new PersistException("Commit failed to write log");
        }
/*        synchronized (this) {
            // wake up any threads waiting on termination of this transaction
            notifyAll();
        } */
        return;
    }
    
    /**
     * Mark this Transaction as 'killed' (by deadlock resolution, for instance).  The next action on this transaction by the owning client will trigger a roll-back. 
     */
    void kill() {
        killed = true;
        
    }
    
    /**
     * Wakes all Threads that are waiting on this Transaction. 
     */
    synchronized void wake() {
        //unblock any waiting threads
        notifyAll();
    }
    
    /**
     * Returns the number of distinct Persistent objects under the control of this Transaction. 
     */
    int numberPersistents() {
        return (persistents.size());
    }
    
    /**
     * If this Transaction is active (NOT rolled-back, committed or killed), returns true, 
     * otherwise returns false. 
     */
    boolean isAlive() {
        return (!(killed || committed || rolledBack));
    }
    
    /**
     * Returns the arbitrary, unique ID of this transaction.  (Debugging use only) 
     */
    long getID() {
        return Id;
    }
    
    /**
     * Add the parameter Persistent object to the control of this Transaction. 
     */
    synchronized void addPersistent(Persistent p) {
        
        persistents.put(p.getKey(), p);
    }
    
    /**
     * Provide human-readable identity of the Transaction 
     */
    public String toString() {
        return Long.toString(Id);
    }
    
    /**
     * Set the value of the Persistent identified by the name and pClass parameters to 
     * the specified value.
     * Takes control of the specified object.  If another Transaction controls the Persistent
     * object at the time of the call, this method blocks until the Persistent object becomes available.
     * Throws a PersistException if the specified value is not of the specified Class, or if the 
     * Transaction is killed by deadlock resolution while attempting the set operation. 
     */
    void setPstValue(String name, Class pClass, java.io.Serializable value) throws PersistException {
        
        Persistent p = takeControl(name, pClass, false);

        p.set (this, value);
    }
    
    /**
     * Return the current value of the Persistent identified by the name and pClass parameters, under the control of this transaction.
     * If no Persistent object exists identified by the
     * parameters, a new object is created, under the control of this Transaction, with null content.
     * Takes control of the specified object.  If another Transaction controls the Persistent
     * object at the time of the call, this method blocks until the Persistent object becomes available.
     * Throws a PersistException if the Transaction is killed by deadlock resolution 
     * while attempting the set operation. 
     */
    Serializable getPstValue(String name, Class pClass) throws PersistException {
        
        Persistent p = takeControl(name, pClass, false);

        return p.getValue(this);
    }
    
    /**
     * Obtain control of the Persistent object specified by the name and pClass parameters. 
     * If another Transaction has control of the specified object, blocks until the Persistent
     * object becomes available.
     * If this transaction is killed in deadlock resolution, performs a roll-back and throws a
     * PersistException.
     * If the existing parameter is true and the specified Persistent object does not exist, does
     * nothing and returns null.
     * If existing is false the specified Persistent object will be created if it does not exist. 
     */
    private Persistent takeControl(String name, Class pClass, boolean existing) throws PersistException {
        Transaction current = null;
        Persistent p = null;        
        Object key = Persistent.makeKey(name, pClass);
        
        if (isAlive()) {
        
            // if we already have the Persistent in our Map, we already own it...
            if (persistents.containsKey(key)) {
                return (Persistent)persistents.get(key);
            }

            if (existing) {
                p = tm.getExistingPersistent(name, pClass);
            } else {
                p = tm.getPersistent(name, pClass);
            }
                    // Note - if the persistent exisits at this point (even if deleted
                    // before we take control of it) getExisting... will return it.
            for (;
                 isAlive() &&
                 p != null &&
                 (current = p.giveControl(this)) != this;
                 p = tm.getPersistent(name, pClass)) { 
                   // NOTE - re-get the persistent from the library in case of deletion.
                    synchronized (this) {
                        try {
                            if (tm.addBlock(this, current)) {
                                this.wait();
                            } else {
                                this.wait (5); // the current owner is being cleaned up, 
                                               // wait to try again.
                            }
                        } catch (InterruptedException ie) {
                        //don't care
                        }
                }
            }
            if (p != null &&
                isAlive()) {
                persistents.put (p.getKey(), p);
            }            
        }
        
        if (!isAlive()) {
            rollBack();
            throw new PersistException("Transaction Killed.");
        }
        return p;
    }
                            
    /**
     * Return the current value of the Persistent identified by the name and pClass parameters, under the control of this transaction.
     * If no Persistent object exists identified by the parameters, a PersistException is thrown.
     * If the specified object is under the control of a different transaction, blocks until the
     * object becomes available. 
     */
    public Serializable getExistingPstValue(java.lang.String name, java.lang.Class pClass)
    throws PersistException {
        Persistent p;
        
        if ((p = takeControl(name, pClass, true)) == null) {
            throw new PersistException ("No such Persistent exists.");
        }

        return p.getValue(this);
    }    
    
    void recycle(TransactionHandle newOwner) {
    
        owner = newOwner;
        killed = false;
        committed = false;
        rolledBack = false;
        persistents.clear();
        created = new Date();
        synchronized (this.getClass()) {
            Id = idSrc++;
        }
    }
    
    private static long idSrc = 0;
    
    private TransactionMgr tm;
    private TransactionHandle owner;
    private boolean killed;
    private boolean committed;
    private boolean rolledBack;
    private Map persistents;
    private Date created;
    private long Id;
    private TransactionLog logger;
    private List toLibrary;
}

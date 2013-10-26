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

import java.util.*;

/**
 * Singleton that constructs and controls the operation of the Persistence engine.
 * Offers an Administration API to clients.  Provides the synchronized control point 
 * for the creation of Transaction and Persistent objects. 
 */
public final class TransactionMgr {
    /**
     * private constructor - enforcing singleton pattern 
     */
    private TransactionMgr() {
        transactions = new HashSet();
        transactionPool = Collections.synchronizedList(new LinkedList());
        transactionBlocks = new HashMap();
        revTransactionBlocks = new HashMap();
        blockLock = new Object();
        // determine the directory containing the log and
        // PO disk images.
        // this may be defined on the command line by -D
        // or in the ResourceBundle which contains the error text
        // or defaults to a subdir of the current directory.
        logDirName = System.getProperty(TransactionMgr.LOGDIR);
        if (logDirName == null) {
            // Try the resources
            if (TmBundle != null)
                try {
                    logDirName = TmBundle.getString(TransactionMgr.LOGDIR);
                } catch (java.util.MissingResourceException e) { }
            if (logDirName == null)
                logDirName = System.getProperty("user.dir") + System.getProperty("file.separator") + "logdir";
        }
        /* should create this if it doesn't exist */
        state = DOWN;
    }

    /**
     * Called to close down all operations of the engine. 
     */
    public void shutDown() {
    	
		if (isUp()) {
			library.shutDown();
			killer.stopRollBack();
			logger.shutDown();
			transactionPool.clear();
			state = DOWN;
		}
    }

    /**
     * Starts the engine, restoring any existing persisted data by re-loading the library and rolling
     * forward the transaction history, as necessary.
     * Throws a PersistException if unable to start. 
     */
    public void warmStart() throws PersistException {

        if (!isUp()) {
        	library = new PersistentLibrary(logDirName, true);
			logger = new TransactionLog(logDirName);
			killer = new rollBack();

			logger.warmStart(library);
			state = UP;
		}
    }

    /**
     * Starts the engine, destroying all previously stored state.
     * Throws a PersistException if unable to start. 
     */
    public void coldStart() throws PersistException {
        
        if (!isUp() ) {
        	library = new PersistentLibrary(logDirName, false);
            logger = new TransactionLog(logDirName);
            killer = new rollBack();
            logger.coldStart();
            state = UP;
        }
    }

    /**
     * Triggers an interim checkpoint of the engine.
     * @deprecated Handled automatically 
     *
    public boolean checkPoint() {
       
        synchronized (ckptlock) {
            if (ckpt) // checkpoint already in progress...
                return false;
            
            // First, stop any new transactions being created
            ckpt = true;
            // Now, wait until all in-flight transactions are complete
            if (!transactions.isEmpty()) {
                try {
//                    System.out.println ("CHECKPOINT - waiting for " + transactions.size() + " transactions");
                    ckptlock.wait(50);  // wait for a little while...
                } catch (InterruptedException e) {}
            }

            // check again - if we can't checkpoint now, someone else will do it later...
            if (transactions.isEmpty()) {
                //OK, we have a free reign - no transactions in flight.
                //first flush the library
                library.checkPoint();
                //next roll over the log
                logger.checkPoint();
            }
                //Lastly, free up handing out new transactions
            ckpt = false;
            ckptlock.notifyAll();
        }
        return true;
    } */

    /**
     * returns true if the engine is operational. 
     */
    boolean isUp() {
        if (state > DOWN)
            return true;
        else
            return false;
    }

    /**
     * Singleton accessor that returns the instance of the TransactionMgr, after implicitly constructing it, if necessary. 
     */
    public static TransactionMgr getInstance() {

        synchronized (TransactionMgr.class) {
            if (instance == null)
            	instance = new TransactionMgr();
        }

        return instance;
    }

    /**
     * Adjust the frequency of hard-synchronizing data to disk.  The argument represents the number of commit() calls completed for each forced sync() to disk.  A value of 1 synchronizes every commit().  Any value less than 1 is treated as 1.
     * A SyncFrequency rate higher than one risks loss of committed transactions in some types of crash.  The maximum loss is 50% chance of one transaction for a Sync Frequency of 2, 33% chance of losing 1 transaction and 33% chance of losing 2 transactions for a Sync Frequency of 3, and so on.
     * The default Sync Frequency is 1. 
     */
    public void setSyncFrequency(int frequency) {
        syncFrequency = frequency;
    }
    
    /** Getter for property syncFrequency.
     * @return Value of property syncFrequency.
     */
    public int getSyncFrequency() {
        return syncFrequency;
    }

    /** recursively walk the block graph from the victim, looking for any repeated entry */
    private boolean walkLocks(Transaction victim, Transaction blocker) {
        Transaction candidate;

        int failSafe = transactionBlocks.size();
        for (candidate = blocker;
             victim != candidate &&
             candidate != null &&
             failSafe >= 0 ;
             failSafe--)
                candidate = (Transaction)transactionBlocks.get(candidate);

        if (candidate != null)
            // Deadlock encountered!
            return true;
        else
            return false;
    }

    /** Find deadlocks When they exist,
 *         select a Transaction to be killed
 *         in order to resolve the deadlock, and kill it */
    private void deadlockResolution(Transaction victim, Transaction blocker)
        throws PersistException {
        if (walkLocks(victim, blocker) == true) {
            Transaction todie;
            // deadlock found...
            // kill the transaction with the fewest pending updates
            // in case of a tie, kill the older
            // NOTE this only tests the victim and its immediate blocker
            if (victim.numberPersistents() == blocker.numberPersistents())
                if ((victim.getDate()).before(blocker.getDate()))
                    todie = victim;
                else
                    todie = blocker;
            else if (victim.numberPersistents() > blocker.numberPersistents())
                todie = blocker;
            else
                todie = victim;

//            System.out.println ("DEADLOCK - BACKGROUND kill of " + todie);
            // we delegate the notification of the transaction to the killer thread - 
            // the current thread may get into a deadlock trying to synchronize on it
           todie.kill();
           killer.newVictim(todie);
        }
        return;
    }

    /**
     * Finds a Persistent object identified by name and class in the PersistentLibrary.
     * Returns null if no matching object exists in the library.
     * Throws a PersistException if the Engine is not up. 
     */
    Persistent getExistingPersistent(String name, Class type) throws PersistException {
        if (state == UP) {
            return (library.read(name, type));
        } else {
            throw new PersistException ("Transaction Manager is DOWN.");
        }
    }

    /**
     * Finds a Persistent object identified by name and class in the PersistentLibrary.
     * If no matching object exists in the library, creates a new one and inserts it in the library before returning.
     * Throws a PersistException if the Engine is not up.
     * NOTE: This is the only place that Persistent objects are constructed in
     * normal operation (other than being restored in a warm-start).
     * The method is synchronized to avoid a race on Persistent creation.
     */
    synchronized Persistent getPersistent(String name, Class type) throws PersistException {
        Persistent temp;
        if ((temp = getExistingPersistent(name, type)) == null) {
            temp = Persistent.persistentFactory(name, type);
            library.addTo(temp);
        }
        return temp;
    }

    /**
     * Removes a Transaction and all of its associated block data from the engine. 
     */
    void removeXaction(Transaction tx) {
//        System.out.println("Removing blocks for " + tx);
        //remove blocks and reverse blocks

        synchronized (blockLock) {
            if (transactionBlocks.containsKey(tx)) { // only on a roll-back
                ArrayList tmp = (ArrayList)revTransactionBlocks.get(transactionBlocks.get(tx));
                tmp.remove(tmp.indexOf(tx));
                transactionBlocks.remove(tx);
            }

            ArrayList rb = (ArrayList) revTransactionBlocks.get(tx);

            if (rb != null) {
                Iterator it = rb.iterator();

                while (it.hasNext()) {
                    Transaction t = (Transaction) it.next();

                    // Psychotic case - a rollback may have destroyed
                    // the block by tx, and a new block for t may have
                    // been created
                    if (tx == transactionBlocks.get(t)) {
                        transactionBlocks.remove(t);
                    }

                    synchronized (t) { //wake up the transaction we unblocked
                        t.notify();
                    }
                }
                revTransactionBlocks.remove(tx);
            }
        }
        synchronized (ckptlock) {
            transactions.remove(tx);
            returnTransaction(tx);
        // if removing the last transaction when a checkpoint
        // is starting - notify the waiting checkpointer.
            if (transactions.isEmpty()) {
                if (ckpt) {
//                    System.out.println ("Removing last transaction - CHECKPOINT can proceed");
                    ckptlock.notifyAll();
                }
            }
            if (!ckpt &&
                logger.recommendCkp())
                checkPoint();
        }
    }

    /**
     * Provide a human-readable summary of transaction blocking data. 
     */
    void printBlocks() {
        System.out.println ("---Start of Block report---");
        System.out.println ("\t---Blocks---");

        synchronized (blockLock) {
            Transaction tmp;
            for (Iterator i = transactionBlocks.keySet().iterator();
                 i.hasNext();) {
                    tmp = (Transaction)i.next();
                    System.out.println ("\t" + tmp + " blocked by  " + transactionBlocks.get(tmp));
            }
            System.out.println ("\t---Blocks---");
            System.out.println ("\t---Reverse Blocks---");
            for (Iterator i = revTransactionBlocks.keySet().iterator();
                 i.hasNext();) {
                    tmp = (Transaction)i.next();
                    System.out.print ("\t" + tmp + " blocks ");
                    for (Iterator j = ((ArrayList)revTransactionBlocks.get(tmp)).iterator();
                         j.hasNext();) {
                         System.out.print (" " + j.next());
                    }
                    System.out.println();
            }
        }
        System.out.println ("\t---Reverse Blocks---");
        System.out.println ("---End of Block report---");


    }

    /**
     * Called to indicate that a transaction commit is under way. 
     */
    void startCommit() {
        ++commitsInFlight;
        return;
    }

    /**
     * Called to indicate that a transaction commit has ended.  Note, this does NOT necessarily indicate success of the commit - it may have failed and ended as a roll-back instead. 
     */
    void endCommit() {
        --commitsInFlight;
    }

    /**
     * Create a new Transaction object.  During a checkpoint, will block until the checkpoint is complete.
     * returns null if the engine is not up.
    */
    Transaction newTransaction(TransactionHandle requester) {
        
        if (isUp()) {
            Transaction temp;
            synchronized (ckptlock) {
                while (ckpt) {
                    try {
                        ckptlock.wait();
                    } catch (InterruptedException e) {}
                }
                
                if (isUp()) {
                    temp = getTransaction(requester);
                    transactions.add (temp);
                } else {
                    temp = null;
                }
            }
            return temp;
        } else {
            return null;
        }
    }
    
    private Transaction getTransaction(TransactionHandle requester) {
        
        if (transactionPool.isEmpty()) {
            return new Transaction(requester);
        } else {
            Transaction tmp = (Transaction)transactionPool.remove(0);
            tmp.recycle(requester);
            return tmp;
        }
    }
    
    private void returnTransaction(Transaction done) {
        
        transactionPool.add(done);
    }

    /**
     * Pass a List of changes to the Library. 
     */
    void changesCommitted(List p) {
        library.processDeletions(p);
        
    }

    /** When a transaction is blocked, add a block item for the victim and a reverse block item for the blocker */
    boolean addBlock(Transaction victim, Transaction blocker)
        throws PersistException {
        ArrayList wk;

        if (victim != null && blocker != null) {
            synchronized (blockLock) {
                if (blocker.isAlive() &&
                    victim.isAlive()) {
                    if (!(transactionBlocks.containsKey(victim))) {
                        transactionBlocks.put(victim, blocker);
                    }
                    if (!(revTransactionBlocks.containsKey(blocker))) {
                        wk = new ArrayList();
                        revTransactionBlocks.put(blocker, wk);
                    } else {
                        wk = (ArrayList)revTransactionBlocks.get(blocker);
                    }
                    if (!(wk.contains(victim))) {
                        wk.add(victim);
                    }                 
                    deadlockResolution(victim, blocker);
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * provides a map of lists to the invoker.  The map is keyed by Class.  Each entry in the map is a list of the names of objects (of the key Class) that are held in the Library.
     * No guarantee is given that any subsequent access to the Library will still find the identified Persistent objects - the results are valid and consistent at the time of retrieval, but may be subsequently modified. 
     */
    public java.util.Map getLibKeys() {
        
        if (state > DOWN &&
            library != null) {
            return library.getAllKeys();
        } else {
            return null;
        }
    }
    
    /** Getter for property logger.
     * @return Value of property logger.
     */
    TransactionLog getLogger() {
        return logger;
    }
        
    /** Triggers an interim checkpoint of the engine.
     */
    private boolean checkPoint() {
        
        synchronized (ckptlock) {
            if (ckpt) // checkpoint already in progress...
                return false;
            
            // First, stop any new transactions being created
            ckpt = true;
            // Now, wait until all in-flight transactions are complete
            if (!transactions.isEmpty()) {
                try {
                    //                    System.out.println ("CHECKPOINT - waiting for " + transactions.size() + " transactions");
                    ckptlock.wait(50);  // wait for a little while...
                } catch (InterruptedException e) {}
            }
            
            // check again - if we can't checkpoint now, someone else will do it later...
            if (transactions.isEmpty()) {
                //roll over the log
                logger.rollLogfile();
            }
            //Lastly, free up handing out new transactions
            ckpt = false;
            ckptlock.notifyAll();
        }
        return true;
    }
    
    /**
     * @link
     * @shapeType PatternLink
     * @pattern Singleton
     * @supplierRole Singleton factory 
     */
    /*# private TransactionMgr _transactionMgr; */

    private static TransactionMgr instance = null;

    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int CHECKPOINT = 2;
    private java.util.ResourceBundle TmBundle;
    private int state;

    public static final String PERSISTSUBDIR = "Library";
    public static final String LOGDIR = "org.brann.persist.logdir";
    public static final int MAX_FILESIZE = 200000;
    
    private int syncFrequency = 100;
    Object blockLock;    
    private TransactionLog logger;
    private int commitsInFlight;
    private boolean ckpt = false;
    private Object ckptlock = new Object();
    private List transactionPool;
    private Set transactions;
    private HashMap transactionBlocks;
    private HashMap revTransactionBlocks;
    private rollBack killer;
    private ResourceBundle TmBundle1;
    private String logDirName;
    PersistentLibrary library;

    /**
     * Class that contains a Thread iterating over a queue of Transactions selected for roll-back by deadlock resolution.  A separate thread is used to avoid possible threading deadlocks due to taking the victim Transaction's monitor to perform the notify() operation. 
     */
    class rollBack implements Runnable {

        rollBack() {
            stop = false;
        rollBackQueue = Collections.synchronizedList(new LinkedList());
            myThread = new Thread(this, "Deadlock Killer");
            myThread.start();
        }

        public void run() {
            while (!stop) {
                this.processKills();
            }

            synchronized(this) {  // the thread performing the shutdown is wait()ing
                notify();
            }
        }

        private void processKills() {
            Transaction victim;
            
            while (rollBackQueue.size() > 0) {
                victim = (Transaction)rollBackQueue.remove(0);
                victim.wake();
            }
            synchronized(rollBackQueue) {
                if (rollBackQueue.size() == 0) {
                    try {
                        rollBackQueue.wait(1000L);
                    } catch (InterruptedException e) {/*System.out.println("Killer, interrupted");*/}
                }
            }
        }

        synchronized void stopRollBack() {
            stop = true;

            myThread.interrupt();
        	while (myThread.isAlive()) {
            	try {
                    wait();
                	myThread.join();
            	} catch (InterruptedException e) {}
            }
        }

        void newVictim (Transaction victim) {
            rollBackQueue.add(victim);
            if (rollBackQueue.size() > 0) {
                synchronized (rollBackQueue) {
                    rollBackQueue.notify();
                }
            }
        }
        Thread myThread;
        boolean stop;
    private List rollBackQueue;
    }
}

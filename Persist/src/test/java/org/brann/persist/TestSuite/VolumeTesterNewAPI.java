/*
 * VolumeTester.java
 *
 * Created on October 9, 2002, 9:13 AM
 */

package org.brann.persist.TestSuite;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionHandle;
import org.brann.persist.TransactionMgr;

/**
 *
 * @author  jbrann
 */
public class VolumeTesterNewAPI {
    
    class toPersist {
        
        toPersist(Class pClass) {
            this.pClass = pClass;
        }
        
        boolean doNext(String key, Serializable payload) {
            try {
/*                Persistent p = handle.getPersistent(key, pClass);
                p.set (handle, payload); */
                handle.setPstValue(key, pClass, payload);
            } catch (PersistException pe) {
                System.out.println ("FAILED on " + key + ": " + pe);
                return false;
            }
            return true;
        }
        
        boolean deleteNext(String key) {
            try {
/*                Persistent p = handle.getPersistent(key, pClass);
                p.set (handle, null); */
                handle.setPstValue(key, pClass, null);
            } catch (PersistException pe) {
                System.out.println ("FAILED on " + key + ": " + pe);
                return false;
            }
            return true;
        }
        Class pClass;
    }
    
    /** Creates a new instance of VolumeTester */
    public VolumeTesterNewAPI(int repsThousands, int syncFrequency, boolean warmStart) throws PersistException {
        
        reps = repsThousands * 1000;
        
        times = new long[reps];

        style = new toPersist(payload.getClass());
        
        if (warmStart) {
            long tm = System.currentTimeMillis();
            TransactionMgr.getInstance().warmStart();        
            tm = System.currentTimeMillis() - tm;
            System.out.println("WarmStart (roll forward): " + tm/1000 + " seconds");
            System.out.println ("Contents: ");
            Map keys = TransactionMgr.getInstance().getLibKeys();
            for (Iterator it = keys.keySet().iterator();
                  it.hasNext();) {
            	Class c = (Class)it.next();
            	System.out.print ("\t" + c + ":");
            	List l = (List)keys.get(c);
            	for (Iterator it2 = l.iterator();
            	     it2.hasNext();) {
            		System.out.println("\t\t " + it2.next());
            	}
            }
        } else {
            TransactionMgr.getInstance().coldStart(); 
        }
        TransactionMgr.getInstance().setSyncFrequency(syncFrequency);
        handle = TransactionHandle.getTransactionHandle();
    }
    
    boolean doTest() {
        
        long b4;
        
        int iterations = 0;
        
        for (boolean ok = true;
             ok && iterations < reps;
             ++iterations) {
                 
             String its = Integer.toString(iterations);
             String pl = new String (payload);
             b4 = System.currentTimeMillis();
             ok = style.doNext(its, pl);

             try {
                 handle.commit();
//                 System.out.println (iterations);
                 times[iterations] = System.currentTimeMillis()-b4;
             } catch (PersistException pe) {
                System.out.println ("FAILED on " + its + ": " + pe);
                return false;
             }
            
             times[iterations] = System.currentTimeMillis()-b4;
             if (iterations%1000 == 0) {
                 System.out.print (".");
             }
        }
        
        System.out.println("");
        
        int iDivisor = reps/1000;
        double dDivisor = (double) iDivisor;
        double avg = 0.0;
        
        for (int x = 0; 
             x < reps;
             x ++) {
                 if (x % iDivisor == 0 && x != 0) {// suppress spurious output of 0th element
                     System.out.println (avg/dDivisor);
                     avg = 0.0;
                 }
                 avg += times[x];
        }

        /*System.exit(0); */
        b4 = System.currentTimeMillis();
        TransactionMgr.getInstance().shutDown();
        System.out.println ("\n\n\nShutDown time:" + (System.currentTimeMillis()-b4));
        
        return true;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        int repsThousands = 100;
        int syncFrequency = 1;
        
        if (args.length > 0) {
            try {
                repsThousands = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {}
        }
        
        if (args.length > 1) {
            try {
                syncFrequency = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {}
        }
        
        boolean warmStart = false;
        
        if (args.length > 2)
            warmStart = true;
        
        try {
            VolumeTesterNewAPI vt = new VolumeTesterNewAPI(repsThousands, syncFrequency, warmStart);

            vt.doTest();
        } catch (PersistException pe) {
            System.err.println ("BARF: " + pe);
        }
    }
    
    String payload = "01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";
//    static String payload = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    int iterations = 0;
    TransactionHandle handle;
    

    int reps;
    long times[];
    static toPersist style;
}

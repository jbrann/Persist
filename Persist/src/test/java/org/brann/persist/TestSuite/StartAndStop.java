/*
 * StartAndStop.java
 *
 * Created on April 4, 2003, 4:40 PM
 */

package org.brann.persist.TestSuite;

import org.brann.persist.TransactionHandle;
import org.brann.persist.TransactionMgr;

/**
 *
 * @author  jbrann
 */
public class StartAndStop {
    
    /** Creates a new instance of StartAndStop */
    public StartAndStop() {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        try {
            TransactionMgr.getInstance().coldStart();
            TransactionMgr.getInstance().shutDown();
            TransactionMgr.getInstance().warmStart();
            TransactionHandle h = TransactionHandle.getTransactionHandle();
            String val = "value";
            h.setPstValue("name", val.getClass(), val);
            h.commit();
            h.setPstValue("WRONG NAME", val.getClass(), "WRONG VALUE");
            TransactionMgr.getInstance().shutDown();
            TransactionMgr.getInstance().warmStart();
            java.util.Map m = TransactionMgr.getInstance().getLibKeys();
            for (java.util.Iterator it = m.keySet().iterator();
                 it.hasNext();) {
                     java.util.List l = (java.util.List)m.get(it.next());
                     for (java.util.Iterator it2 = l.iterator();
                          it2.hasNext();) {
                              System.out.println ("<" + it2.next() + "> ");
                     }
            }
        } catch (Exception e) {
            System.out.println("Barf: " + e);
        }
    }
    
}

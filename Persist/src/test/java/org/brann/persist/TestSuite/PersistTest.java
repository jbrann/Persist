package org.brann.persist.TestSuite;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionHandle;
import org.brann.persist.TransactionMgr;
import org.junit.Test;

public class PersistTest {

	@Test
	public void testPersistThreads() {

		for (int count=1;count < 20;++count) {
			for (int syncFreq=0;syncFreq<21;syncFreq+=20) {
			
				assertTrue (PstTestBedNewAPI.doTest(count, true, syncFreq));
				assertTrue (PstTestBedNewAPI.doTest(count, false, syncFreq));
			}	
		}
		
	}

	@Test
	public void testRollback() {
		
		RollbackTesterNewAPI rt;
		try {
			rt = new RollbackTesterNewAPI();
			
			assertTrue(rt.doTest());
		} catch (PersistException e) {
			
			fail("Could not initiate rollback test");
		}

	}

	@Test
	public void testStartAndStop() {
		
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
            Map<Class<?>, LinkedList<String>> m = TransactionMgr.getInstance().getLibKeys();
            for (java.util.Iterator it = m.keySet().iterator();
                 it.hasNext();) {
                     java.util.List l = (java.util.List)m.get(it.next());
                     for (java.util.Iterator it2 = l.iterator();
                          it2.hasNext();) {
                              System.out.println ("<" + it2.next() + "> ");
                     }
            }
        } catch (Exception e) {
            fail("Barf: " + e);
        }


	}
}

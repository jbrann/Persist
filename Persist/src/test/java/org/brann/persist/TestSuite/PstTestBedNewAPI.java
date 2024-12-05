package org.brann.persist.TestSuite;

import java.util.Map;
import java.util.List;
import java.util.Iterator;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionMgr;

public class PstTestBedNewAPI {

	final static int MAXACTORS = 30;

	static int count;
	static ActorNewAPI[] aa;
	static Thread[] ta;
	static String[] sa = { "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
				"l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w",
				"x", "y", "z", "0", "1", "2", "3" };


	/**
	 * Constructor.
	 */
	public PstTestBedNewAPI() {
	}

	public static void main(String args[]) {

		PstTestBedNewAPI x;

		int syncFreq=0;
		boolean cold = false;

		

//		x = new PstTestBedNewAPI();

		if (args.length > 0)
			count = Integer.parseInt(args[0]);

		if (count <= 0)
			count = 1;
		else if (count > MAXACTORS)
			count = MAXACTORS;

		if (args.length > 1) {
			if (args[1].startsWith("c")) {
				cold = true;
			}
		}
		
		if (args.length > 2) {
			syncFreq = Integer.parseInt(args[2]);
		}
			
		doTest(count, cold, syncFreq);
		
		System.out.println("Actors complete - shutting down");
		
		System.out.println("End of Persist testbed");

	}
	
	static boolean doTest (int threadCount, boolean cold, int syncFrequency) {
		
		int si, ti, one, two, three;
		
		TransactionMgr tMgr = TransactionMgr.getInstance();
		
		aa = new ActorNewAPI[threadCount];
		ta = new Thread[threadCount];

		for (int i = 0; i < 2; ++i) { // do the whole cycle twice...

			try {
				if (cold) {
					tMgr.coldStart();
				} else {
					tMgr.warmStart();
	//					printKeys();
				}
			} catch (PersistException pe) {
				return false;
			}
			
			if (syncFrequency != 0) {
				tMgr.setSyncFrequency(syncFrequency);
			}
			
			for (si = 0, ti = threadCount - 1; ti >= 0; --ti) {

				one = nextSi(si);
				two = nextSi(one);
				three = nextSi(two);

				si = three;

				aa[ti] = new ActorNewAPI(sa[one], sa[two], sa[three]);

				ta[ti] = (aa[ti]).getThread();
			}

			try {
				java.lang.Thread.sleep(100);
			} catch (Exception e) {
				System.out.println(e);
			}
			// tMgr.checkPoint();
			printKeys(); // ??

			// wait for the threads to finish...

			for (ti = threadCount - 1; ti >= 0; --ti) {

				try {
					ta[ti].join();
				} catch (InterruptedException e) {
				}
			}
			
			tMgr.shutDown();
		}
		return true;
	}

	private static int nextSi(int si) {

		if (count % 3 == 0) {
			if (si >= (count - 2))
				si = 0;
			else
				++si;
		} else {
			if (si >= (count - 1))
				si = 0;
			else
				++si;
		}

		return si;
	}

	static void printKeys() {
		Map keys = TransactionMgr.getInstance().getLibKeys();
		if (keys == null || keys.isEmpty()) {
			System.out.println("Library Empty after WARM START");
		} else {
			for (Iterator it = keys.keySet().iterator(); it.hasNext();) {
				Class c = (Class) it.next();
				System.out.print("Class <" + c.getName()
						+ "> has objects named:\t");
				for (Iterator nIt = ((List) keys.get(c)).iterator(); nIt
						.hasNext();) {
					System.out.print(nIt.next() + " ");
				}
				System.out.println("");
			}
		}
	}
}

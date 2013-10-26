package org.brann.persist.TestSuite;

import java.util.Map;
import java.util.List;
import java.util.Iterator;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionMgr;

public class PstTestBedNewAPI {

	final static int MAXACTORS = 30;

	static int count;

	/**
	 * Constructor.
	 */
	public PstTestBedNewAPI() {
	}

	public static void main(String args[]) {

		ActorNewAPI[] aa;
		Thread[] ta;

		PstTestBedNewAPI x;

		int si, ti, one, two, three;

		String[] sa = { "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k",
				"l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w",
				"x", "y", "z", "0", "1", "2", "3" };

		TransactionMgr tMgr = TransactionMgr.getInstance();

		x = new PstTestBedNewAPI();

		if (args.length > 0)
			count = Integer.parseInt(args[0]);

		if (count <= 0)
			count = 1;
		else if (count > MAXACTORS)
			count = MAXACTORS;


		if (args.length > 2) {
			try {
				tMgr.setSyncFrequency(Integer.parseInt(args[2]));
			} catch (NumberFormatException nfe) {
			}
		}

		aa = new ActorNewAPI[count];
		ta = new Thread[count];

		for (int i = 0; i < 2; ++i) { // do the whole cycle twice...

			try {
				if (args.length > 1 && args[1].startsWith("c"))
					tMgr.coldStart();
				else {
					tMgr.warmStart();
					printKeys();
				}
			} catch (PersistException pe) {
				System.err.println(pe);
				System.exit(-1);
			}
			
			for (si = 0, ti = count - 1; ti >= 0; --ti) {

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

			for (ti = count - 1; ti >= 0; --ti) {

				try {
					ta[ti].join();
				} catch (InterruptedException e) {
				}
			}

			System.out.println("Actors complete - shutting down");
			tMgr.shutDown();
		}
		System.out.println("End of Persist testbed");

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

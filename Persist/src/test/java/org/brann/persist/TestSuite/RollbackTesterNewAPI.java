/*
 * VolumeTester.java
 *
 * Created on October 9, 2002, 9:13 AM
 */

package org.brann.persist.TestSuite;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionHandle;
import org.brann.persist.TransactionMgr;

/**
 *
 * @author jbrann
 */
@SuppressWarnings("serial")
public class RollbackTesterNewAPI implements java.io.Serializable {

	/** Creates a new instance of VolumeTester */
	public RollbackTesterNewAPI() throws PersistException {

		TransactionMgr.getInstance().coldStart();
		handle = TransactionHandle.getTransactionHandle();
	}

	@SuppressWarnings("unused")
	boolean doTest() {

		String myPersistName = "foo";
		Content c = new Content();
		Class<?> cClass = c.getClass();

		try {

//        Persistent p = handle.getPersistent(myPersistName, cClass);
//        p.set(handle, c);

			handle.setPstValue(myPersistName, cClass, c); // ?? new API

//        System.out.println ("p has new Content: " +((Content)p.getValue(handle)).getValue());

			System.out.println(
					"Persistent Object has new Content: " + ((Content) handle.getPstValue(myPersistName, cClass))); // ??
																													// new
																													// API
			c.setValue(c.getValue() + 1);
			handle.commit();
			System.out.println("After commit:");
			System.out.println(
					"Persistent Object has new Content: " + ((Content) handle.getPstValue(myPersistName, cClass)));

//        Persistent q = handle.getPersistent(myPersistName, cClass);
//        System.out.println ("q has Content: " + ((Content)q.getValue(handle)).getValue());
//        Content d = (Content)q.getValue(handle);

			Content d = (Content) handle.getExistingPstValue(myPersistName, cClass); // ?? new API
			d.setValue(d.getValue() + 1);
//        q.set(handle, d);
			handle.setPstValue(myPersistName, cClass, d); // ?? new API
//        System.out.println ("q has Content: " + ((Content)q.getValue(handle)).getValue());
			System.out.println("New Persistent has Content: " + ((Content) handle.getPstValue(myPersistName, cClass)));
			System.out.println("Original Persistent has Content: " + c.getValue());
			handle.rollBack();
			System.out.println("After rollback:");
//        System.out.println ("q has Content: " + ((Content)q.getValue(handle)).getValue());
			System.out.println("New Persistent has Content: " + ((Content) handle.getPstValue(myPersistName, cClass)));
			System.out.println("Original Persistent has Content: " + c.getValue());

			// check exception is thrown:
			try {
				Content x = (Content) handle.getExistingPstValue("DeliberatelyBogusName", cClass);
				throw new Exception("Didn't get an exception");
			} catch (PersistException pe) {
				System.out.println("Expecting 'No such object', got:\n" + pe);
			}

			TransactionMgr.getInstance().shutDown();
		} catch (Exception e) {
			System.out.println("oops " + e);
			return false;
		}
		return true;
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {

		try {
			RollbackTesterNewAPI rt = new RollbackTesterNewAPI();
			rt.doTest();
		} catch (PersistException pe) {
			System.err.println("No can do.");
		}
	}

	TransactionHandle handle;

}

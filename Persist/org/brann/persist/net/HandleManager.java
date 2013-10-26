package org.brann.persist.net;

import java.util.Collections;
import java.util.HashMap;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionHandle;
import org.brann.persist.TransactionMgr;

/**
 * Singleton maintaining a list of existing transaction handles
 * @author john
 *
 */
public class HandleManager {
	
	private HandleManager() {  //private singleton constructor
		
		tMgr = TransactionMgr.getInstance();
		
		try {
			tMgr.warmStart();
		} catch (PersistException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		handleMap = Collections.synchronizedMap(new HashMap<String, org.brann.persist.TransactionHandle>()); 		
	}

	public static synchronized HandleManager getHandleManager() {
		
		if (instance == null) {
			instance = new HandleManager();
		}
		return instance;		
	}
	
	public String newTransactionHandle() {
		
		org.brann.persist.TransactionHandle newHandle = TransactionHandle.getTransactionHandle();
		String handleId = Integer.toString(newHandle.hashCode());
		handleMap.put(handleId, newHandle);
		
		return handleId;
	}
	
	public void release (String id) {
		handleMap.remove(id);
	}
	
	public TransactionHandle get (String id) throws PersistException {
		
		if (handleMap.containsKey(id)) return handleMap.get(id);
		else throw new PersistException ("No such transactionHandle");
	}
	
	static HandleManager instance;
	static TransactionMgr tMgr;
	java.util.Map<String, TransactionHandle> handleMap;

}

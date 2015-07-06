package org.brann.persist.net;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionHandle;
import org.brann.persist.TransactionMgr;

public class PersistWebServiceImpl {
	
	public String getTransactionHandle(String alias) {
		
		return HandleManager.getHandleManager().newTransactionHandle();
	}
	
	public void releaseTransactionHandle(String handle) {
		HandleManager.getHandleManager().release(handle);
	}
	
	public byte[] getExistingValue (String handle, String objName, String sourceClass) throws PersistException{
		
		TransactionHandle th = HandleManager.getHandleManager().get(handle);
		ObjectWrapper ow = (ObjectWrapper)th.getExistingPstValue(mkObjName(sourceClass, objName), String.class);
		if (ow != null) {
			return ow.getValue();
		} else return null;
	}

	public byte[] getValue (String handle, String objName, String sourceClass) throws PersistException {
		
		TransactionHandle th = HandleManager.getHandleManager().get(handle);
		ObjectWrapper ow = (ObjectWrapper)th.getPstValue(mkObjName(sourceClass, objName), ObjectWrapper.class);
		if (ow != null) {
			return ow.getValue();
		} else return null;
	}
	
	public boolean setValue (String handle, String objName, String sourceClass, byte[] object) throws PersistException {
		
		TransactionHandle th = HandleManager.getHandleManager().get(handle);
		ObjectWrapper wrapper = new ObjectWrapper(sourceClass, object);
		th.setPstValue(mkObjName(sourceClass, objName), ObjectWrapper.class, wrapper);
		return true;
	}
	
	public boolean commit(String handle) {
		
		try {
			HandleManager.getHandleManager().get(handle).commit();
			return true;
		} catch (PersistException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean rollback(String handle) {
		
		try {
			HandleManager.getHandleManager().get(handle).rollBack();
			return true;
		} catch (PersistException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
	
	public boolean startUp (boolean warm) {
		
		try {
			if (warm) {
				TransactionMgr.getInstance().warmStart();
			} else {
				TransactionMgr.getInstance().coldStart();
			}
			return true;
		} catch (PersistException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
	
	public void setSyncFreq (int freq) {
		
		TransactionMgr.getInstance().setSyncFrequency(freq);
	}
	
	public boolean startUp (boolean warm, int freq) {
		
		TransactionMgr.getInstance().setSyncFrequency(freq);
		return startUp(warm);
	}
	
	public boolean shutDown () {
		TransactionMgr.getInstance().shutDown();
		return true;
	}
	
	private String mkObjName (String sourceClass, String srcName) {
		
		StringBuffer sb = new StringBuffer(sourceClass);
		sb.append('\0');
		sb.append(srcName);
		
		return sb.toString();
		
	}
}

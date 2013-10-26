package org.brann.persist.TestSuite;

import org.brann.persist.PersistException;
import org.brann.persist.TransactionHandle;


public class ActorNewAPI implements Runnable {

private static int id = 0;

private int myid;
private Thread myThread;
private TransactionHandle handle;
private String name1;
private String name2;
private String name3;
private Class persistClass1;
private Class persistClass2;

    public ActorNewAPI(String first, String second, String third) {


	myid = id++;

	try {
	    persistClass1 = Class.forName ("java.lang.Integer");
	    persistClass2 = Class.forName ("java.lang.String");
	} catch (java.lang.ClassNotFoundException e) 
	    {System.out.println ("Feh!! " + e);
	    return;}
									  
	handle = TransactionHandle.getTransactionHandle();
	    
/*	a = tm.getPersistent(first, persistClass1);
	b = tm.getPersistent(second, persistClass1);
	c = tm.getPersistent(third, persistClass1); */
        
        name1 = first;
        name2 = second;
        name3 = third;
	
	myThread = new Thread(this);
	myThread.start();
    }

     Thread getThread() {

	return myThread;
    }


    public void run() {
	boolean success = false;
	boolean failure = false;

	Thread.yield();

	System.out.println ("Hi: I'm thread " + myid +
			    " and I am going to change:\n" +
			    "\t\t"+ name1 + " to 1\n" +
			    "\t\t"+ name2 + " to 2\n" +
			    "\t\t"+ name3 + " to deleted");
	
	for (int cntr = 0;
	     cntr < 50;
	     success = false, failure = false) {
	    while (handle != null &&
		   !success   &&
		   !failure) {
		
		try {
		    System.out.println ("Hi: I'm thread " + myid + " and the current values are:\n\t\t" + 
                    name1 + ": " + handle.getPstValue(name1, persistClass1) + "\n\t\t" +
                    name2 + ": " + handle.getPstValue(name2, persistClass1) + "\n\t\t" +
                    name3 + ": " + handle.getPstValue(name3, persistClass1));

		    
		//    System.out.println ("Hi: I'm thread " + myid + " and I'm making my first change to " + a);
                    handle.getPstValue(name1, persistClass1);
                    handle.setPstValue(name1, persistClass1, new Integer(1));
		    
		    Thread.yield();
		    
		    /*		try {
				myThread.sleep (1000);
				} catch (java.lang.InterruptedException e) {} */
		    
		//    System.out.println ("Hi: I'm thread " + myid + " and I'm making my second change to " + b.getName());
                    handle.getPstValue(name2, persistClass1);
                    handle.setPstValue(name2, persistClass1, new Integer(2));
		    
		    Thread.yield();
		    
		//    System.out.println ("Hi: I'm thread " + myid + " and I'm making my third change to " + c.getName());
                    handle.getPstValue(name3, persistClass1);
                    handle.setPstValue(name3, persistClass1, null);
		    
		    /*		try {
				myThread.sleep (1000);
				} catch (java.lang.InterruptedException e) {} */
		    
		    Thread.yield();
		    
		    System.out.println ("Hi: I'm thread " + myid + " and I'm going to commit transaction " + handle.getID() + " now");
		    handle.commit();
		    
		    
		    success = true;
		}  catch (PersistException e) {System.out.println ("THREAD " + myid + e); failure = true;}
	    }

	if (success) {
	    System.out.println ("Hi: I'm thread " + myid + " and I SUCCEEDED");
        ++cntr;
    }
	if (failure) {
	    System.out.println ("Hi: I'm thread " + myid + " and I FAILED");
	    Thread.yield();
	}
	}
	    System.out.println ("\n\nHi: I'm thread " + myid + " and I ENDED\n\n");
    }
}


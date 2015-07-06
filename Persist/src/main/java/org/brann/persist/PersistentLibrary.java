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

import java.io.*;
import java.nio.channels.FileLock;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;

/**
 * The central memory store of all Persistent objects.  Responsible for saving the store
 * to disk in a shutdown and restoring it in a warm start. 
 */
class PersistentLibrary {    
    /**
     * Constructs the PersistentLibrary.  if rebld is true, loads the content from a Library
     * saved by a previous shutdown.
     * The libDir parameter contains the full path name of the directory that will contain all of the 
     * files (library, transaction log and lock file) used by this engine.
     * If another process is running a TransactionMger with its PersistentLibrary in the named
     * directory, a PersistException is thrown and the library is not created. 
     */
    @SuppressWarnings("resource")
	PersistentLibrary(String libDir, boolean reBuild)
        throws PersistException {
        String reason = null;
        
        libName = libDir + File.separator + "Library";
        lib = new File (libName);
        if (!(lib.exists())) {
            try {
                File libDirFile = new File(libDir);
                libDirFile.mkdirs();
            } catch (Exception e) {System.out.println (e);} 
        }
        // establish a lock on an arbitrary file in the directory - 
        // this ensures exclusive access for this JVM.
        try {
            lock = new FileOutputStream(libDir + File.separator + "Lock").getChannel().tryLock();
            if (lock == null) {
                reason = "Another JVM is using my library in: " + libDir;
            }
        } catch (Exception e) {
            lock = null;
            reason = e.toString();
        } finally {
            if (lock == null) {
                System.err.println ("Can't start PersistentLibrary: " + reason);
                throw new PersistException("Can't Start");
            }
        }
//        flusher = new PersistentFlusher (libDir);
        initLibrary(reBuild);
    }

    /**
     * Builds an empty library.  If the argument is true, attempts to populate from a saved
     * Library file.  Any restoration failures are logged, and may result in objects not being
     * restored, but this method will always succeed. 
     */
    @SuppressWarnings("unchecked")
	private synchronized void initLibrary (boolean rebuild) {
        
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        InflaterInputStream iis = null;
        ObjectInputStream ois = null;
        
        classLibrary = new HashMap<Class<?>, Map<String, Persistent>>();  // This will be blown away if we succeed in reading
                                       // from disk - but that might fail (if we weren't
                                       // closed down cleanly) - sp provide an empty one
        
        if (rebuild) {
            try {
                fis = new FileInputStream(lib);
                bis = new BufferedInputStream(fis);
                iis = new InflaterInputStream(bis);
                ois = new ObjectInputStream (iis);
                classLibrary = (HashMap<Class<?>, Map<String, Persistent>>)ois.readObject();
            } catch (IOException ioe) {
                System.err.println ("IO problem restoring library zip: " + ioe);
            } catch (ClassNotFoundException cnfe) {
                System.err.println ("Problem restoring class in library: " + cnfe);
            } finally {
                try {
                    if (ois != null)
                        ois.close();
                } catch (Exception e) {}
                try {
                    if (iis != null)
                        iis.close();
                } catch (Exception e) {}
                try {
                    if (bis != null)
                        bis.close();
                } catch (Exception e) {}
                try {
                    if (fis != null)
                        fis.close();
                } catch (Exception e) {}
            }
//            flusher.replay(this);
        } else {
            try {
                lib.delete();
//                flusher.coldStart();
            } catch (Exception e) {System.out.println(e);
            }
        }
    }
    
    /**
     * Performs a final shutdown of the library.
     * Writes out the library contents to a library file and causes all intermediate 
     * checkpoint images to be destroyed. 
     */
    synchronized void shutDown() {
    
        try {
                FileOutputStream poFile = new FileOutputStream(lib);
                java.io.FileDescriptor poFD = poFile.getFD();                
                DeflaterOutputStream poZip = new DeflaterOutputStream(new BufferedOutputStream(poFile));
                ObjectOutputStream poObj = new ObjectOutputStream(poZip);
                
                poObj.writeObject(classLibrary);
                poObj.flush();
                poZip.finish();
                poZip.flush();
                poFD.sync();
                lock.release();
                poFile.close();
                
                lock.close();
                
//                flusher.shutDown();  
        } catch (Exception e) {
            System.err.println ("AAAARGH - failed to shut down library!" + e);
            e.printStackTrace();
        } 
                
        return;
    }

    /**
     * Apply the parameter List of Persistent objects to the library.  Any that are now null are deleted from the library.  All are passed to the PersistentFlusher for the next checkpoint. 
     */
    synchronized void processDeletions(java.util.List<PersistentLogEntry> changed) {
       
        for (PersistentLogEntry pdi : changed) {
        	if (pdi.isNull()) {
        		removePersistent(pdi);
        	}
        }
    }
    
    /**
     * remove the Persistent Object derived from the parameter LogEntry from the library. 
     */
    private synchronized void removePersistent(PersistentLogEntry victim) {
        
        Map<String, Persistent> mapForClass;
        Class<?> vClass = victim.getPersistentClass();
        
        if ((mapForClass = classLibrary.get(vClass)) != null) {
            mapForClass.remove(victim.getName());

            if (mapForClass.isEmpty()) {
                classLibrary.remove(vClass);
            }
                
        }
    }

    /**
     * Return the Persistent object specified by the arguments.  If none matches, return null. 
     */
    synchronized Persistent read(String name, Class<?> type) {
        
        Map<?, ?> mapForClass;
        
        if ((mapForClass = classLibrary.get(type)) != null) {
            return ((Persistent) mapForClass.get(name));
        }
        
        return null;
    }

    /**
     * Store the argument Persistent Object in the library.
     * NOTE - this will silently replace any existing Persistent object of the same name and class. 
     */
    synchronized void addTo(Persistent item) throws PersistException {
        
        Class<?> pClass;
        Map<String, Persistent> libForClass;
        
        if (!classLibrary.containsKey(pClass = item.getPersistentClass())) {
            classLibrary.put (pClass, (libForClass = new HashMap<String, Persistent>()));
        } else {
            libForClass = classLibrary.get(pClass);
        }
        
        libForClass.put (item.getName(), item);
    }
    
    /**
     * Instruct the PersistentFlusher to perform a Checkpoint. 
     *
    void checkPoint() {
        
//        flusher.flush();
        
    } */
    
    /**
     * 'dirty-ish' read of all the keys in the library.
     * returns a Map of Lists - Map is keyed by Class.  Each List contains the 
     * name String of each of the Persistent objects of that class in the
     * library.  The order of the names is not defined.
     * NO GUARANTEE THAT ANY OF THESE KEYS IS STILL PRESENT WHEN YOU COME TO USE IT.
     */
    synchronized Map<Class<?>, LinkedList<String>> getAllKeys() {
      
        
        Map<Class<?>, LinkedList<String>> rv = new HashMap<Class<?>, LinkedList<String>>();
        
        for (Class<?> cls : classLibrary.keySet()) {
                 
                 rv.put (cls, new LinkedList<String>(classLibrary.get(cls).keySet()));
        }
        return rv;                 
    }


    private String libName;
    private java.io.File lib;
    private FileLock lock;
    private HashMap<Class<?>, Map<String, Persistent>> classLibrary;
}

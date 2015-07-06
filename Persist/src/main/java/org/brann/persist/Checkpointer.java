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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.EOFException;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

/**
 * Class that builds and restores the checkpoint summaries of engine activity.
 * All the Persistent objects that are changed are stored in a Map until a checkpoint occurs.  
 * In a checkpoint, the Map is written out as a ZipEntry in a checkpoint file and cleared. 
 */
class Checkpointer implements Runnable {
    /**
     * Builds a new Checkpointer that will checkpoint to the same directory as the argument Transaction Log
     */
    Checkpointer(TransactionLog log) {
        pendingFlushes = Collections.synchronizedList(new LinkedList<List<PersistentLogEntry>>());
        currentFlush = new LinkedList<PersistentLogEntry>();
        pendingFlushes.add(0, currentFlush);
        numFlushes = 0;
        zos = null;
        ckpFileSeq = 0;
        this.log = log;
        lock = new Object();
    }
    
    void startCheckpointer() {
        
        if (shutDown ||
            (myThread != null &&
             myThread.isAlive())) {
                return;
        }
        
        shutDown = false;
        myThread = new Thread(this, "Checkpointer");
        myThread.setPriority(Thread.MIN_PRIORITY);
        myThread.setDaemon(true);
        myThread.start();
    }
    
    /**
     * Constructs the OutputStreams for a new checkpoint file. 
     */
    private void buildStreams() {
        try {
            ckpFile = new RandomAccessFile(log.getFileName(CKP_ZIPNAME, ++ckpFileSeq), "rw");
            libFd = ckpFile.getFD();
            fos = new FileOutputStream(libFd);
            zos = new ZipOutputStream(fos);
            zos.setLevel(java.util.zip.Deflater.BEST_SPEED);
        } catch (Exception e) {
            System.err.println ("Can't build streams to do interim library checkpoints: " + e);
        }
    }
    
    /**
     * closes the streams on the current checkpoint file. 
     */
    private void closeStreams() {
            
        if (zos != null) {
                try {
                zos.close();
            } catch (Exception e) {
            System.err.println ("Problem closing Zip Output Stream: " + e);
            }
        }
    }
    
    /**
     * Adds the changed object to the checkpoint collection. 
     */
    synchronized void addChanged(PersistentLogEntry ple) {
        
        currentFlush.add(ple);
    }
    
    
    /**
     * closes the current checkpoint file and destroys all the checkpoint files. 
     */
    void shutDown() {
        
        shutDown = true;
        synchronized(lock) {
            lock.notify();
        }
        try {
            myThread.join();
        } catch (InterruptedException ie) {
        }
        closeStreams();
        log.destroyFiles(CKP_ZIPNAME);
    }
    
    /**
     * Performs start-up operation that does not restore any checkpoint files. 
     */
    void coldStart() {
        log.destroyFiles(CKP_ZIPNAME);
        startCheckpointer();
    }
    
    void warmStart(PersistentLibrary lib) {
        
        replay(lib);
    }
    
    /**
     * Read through all the existing checkpoint files, in order, and restore the Persistent Objects in them to the Library. 
     */
    private void replay(PersistentLibrary lib) {

        File[] ckpFiles = log.getFiles(CKP_ZIPNAME);  // assumes that this file list is in alphabetical order of file names.
        ckpFileSeq = log.extractSequence(ckpFiles, CKP_ZIPNAME, false); // when we start writing files, we will begin with the 'next' file.
        
        for (int  x = 0;
             x < ckpFiles.length;
             ++x) {
                 if (buildReadStream(ckpFiles[x])) {
                     try {
                         while ((zis.getNextEntry()) != null) {
                             ObjectInputStream ois = new ObjectInputStream(zis);
                             @SuppressWarnings("unchecked")
							List<LogEntry> restoredList = (List<LogEntry>)ois.readObject();
                             zis.closeEntry();
                             // NOTE!!  Even though we'return finished with ois here,
                             // we CAN'T close it - that would close the whole stack of streams, starting with zis.
                             if (restoredList != null) {
                                 for (LogEntry pdi : restoredList) {
                                           
                                          try {
                                            lib.addTo(Persistent.persistentFactory((PersistentLogEntry)pdi));
                                          } catch (PersistException pe) {
                                              System.err.println ("Problem restoring Persistent object: " + pe);
                                          }
                                 }
                             }
                         }
                     } catch (EOFException eofe) {
                         /* OK */
                     } catch (IOException ioe) {
                         System.err.println ("Problem getting object from checkpoint zipfile: " + ioe);
                     } catch (ClassNotFoundException cnfe) {
                         System.err.println ("Problem getting object from zipfile: " + cnfe);
                     } catch (ClassCastException cce) {
                         System.err.println ("Data problem in checkpoint file " + ckpFiles[x] + ": " + cce);
                     }
                 } else {
                     System.err.println ("Unable to restore checkpoint file ckp" + x);
                 }
        }
        if (zis != null)
            closeReadStream();
        
       return; 
    }
    
    /**
     * Build InputStreams used in reading a checkpoint file. 
     */
    boolean buildReadStream(File f) {
        
        try {
            fis = new FileInputStream(f);
            bis = new BufferedInputStream(fis);
            zis = new ZipInputStream(bis);
        } catch (IOException ioe) {
            System.err.println ("Can't build input streams for replay file: " + ioe);
            return false;
        }
        return true;
    }
    
    /**
     * Destroy the InputStreams used in reading a checkpoint file. 
     */
    void closeReadStream() {
        try {
            zis.close();
            bis.close();
            fis.close();
        } catch (IOException ioe) {
            System.err.println ("Can't close input streams for replay file: " + ioe);
        }
    }
    
    /** When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see     java.lang.Thread#run()
     */
    
    public void run() {
        
        while (!shutDown) {
        
            synchronized (lock) {
                if (pendingFlushes.size() < 2) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ie) {}
                }
            }

            if (pendingFlushes.size() >= 2) {
                if (zos == null) 
                    buildStreams();

                try {
                    zos.putNextEntry(new ZipEntry(Integer.toString(++numFlushes)));
                    zos.flush();
                    ObjectOutputStream oos = new ObjectOutputStream(zos);
                    oos.writeObject(pendingFlushes.get(0));
                    oos.flush();
                    zos.closeEntry();
                    zos.flush();
  //                  zos.finish();
                    libFd.sync();
                    if (ckpFile.length() > TransactionMgr.MAX_FILESIZE) {
                        closeStreams();
                        buildStreams();
                    }
                } catch (IOException ioe) {
                    // unable to build the zip - dont zap the log
                    continue;
                }
                log.destroyFile(TransactionLog.LOGFILENAME, log.getLogLowSequence());
                log.setLogLowSequence();
                pendingFlushes.remove(0); // lose the pending structure we just flushed
            }
        }
    }
    
    /**
     * the transaction log rolled over, so add a new List of pendings for the new log
     */
    public void logAvailable() {
        
       currentFlush = new LinkedList<PersistentLogEntry>();
       pendingFlushes.add(currentFlush);
       
       // at least one log is now available to be flushed...
        synchronized (lock) {
            lock.notify();
        }
    }
    
    private static final String CKP_ZIPNAME = "ckp";

    private static int numFlushes;

    
    private List<List<PersistentLogEntry>> pendingFlushes;
    private List<PersistentLogEntry> currentFlush;
    private int ckpFileSeq;
    private RandomAccessFile ckpFile;
    private FileOutputStream fos;
    private FileDescriptor libFd;
    private ZipOutputStream zos;
    private FileInputStream fis;
    private BufferedInputStream bis;
    private ZipInputStream zis;
    private TransactionLog log;
    private Thread myThread;
    private Object lock;
    private boolean shutDown;
}


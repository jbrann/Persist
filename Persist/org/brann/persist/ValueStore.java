/*
 * Copyright (c) 2003 John Brann.  All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.util.LinkedList;
import java.util.List;
import java.util.Collections;
/**
 *
 * Singleton used to convert between objects and their 'frozen' form held between
 * transactions.
 * @author  jbrann
 */
class ValueStore {
    
    class OutStreams {
        ByteArrayOutputStream bos;
        ObjectOutputStream oos;
    }
    
    class InStreams {
        ReusableByteArrayInputStream bis;
        ObjectInputStream ois;
    }
    
    /**
     * Extension of java.io.ByteArrayInputStream that allows the byte array source to be
     * replaced without creating a new ByteArrayInputStream object. 
     */
    class ReusableByteArrayInputStream extends java.io.ByteArrayInputStream {
        
        public ReusableByteArrayInputStream(byte[] buf) {
            super(buf);
        }
        
        public void setArray(byte[] buf) {
            this.buf = buf;
            this.pos = 0;
            this.count = buf.length;
            this.mark = 0;
        }
        
    }
    
    protected ValueStore(){
        inPool = Collections.synchronizedList(new LinkedList());
        outPool = Collections.synchronizedList(new LinkedList());
   }

    protected synchronized void extendPool() throws PersistException {
        InStreams is = new InStreams();
        OutStreams os = new OutStreams();
        try {
            os.bos = new ByteArrayOutputStream();
            os.oos = new ObjectOutputStream(os.bos);
            is.bis = new ReusableByteArrayInputStream(os.bos.toByteArray());
            is.ois = new ObjectInputStream(is.bis);
            inPool.add(is);
            outPool.add(os);
        } catch (java.io.IOException ioe) {
            throw new PersistException (ioe.toString());
        }
    }
    
    /**
     * 'Freeze' the argument object into a byte array and return the result.
     * Throws a PersistException if unable to comply with the request. 
     */
    byte[] store (Serializable val) throws PersistException {
        
        OutStreams out = getOstreams();
        try {
            out.bos.reset();  // write at byte[0] of the byte array
            out.oos.reset();  // write a reset marker
            out.oos.writeObject(val);
            out.oos.flush(); // flushes down the i/o layers.
            byte[] result = out.bos.toByteArray();
            returnOstreams(out); // note, if an exception is thrown these streams are discarded.
            return result;
        } catch (Exception e) {
            throw new PersistException(e.toString());
        }
    }

    /**
     * Retrieve an object from the argument frozen form. Throws
     * a PersistException if unable to retrieve the object. 
     */
    Serializable retrieve (byte[] val) throws PersistException {
        
        InStreams in = getIstreams();
        try {
            in.bis.setArray(val);  // use the argument as the data source
            Serializable result = (Serializable)in.ois.readObject(); // read the object
            returnIstreams(in);
            return result;
        } catch (Exception e) {
            throw new PersistException (e.toString());
        }            
    }
    
    synchronized InStreams getIstreams() throws PersistException {
        
        if (inPool.size() == 0) {
            extendPool();
        }
        return (InStreams)inPool.remove(0);
    }
    
    synchronized OutStreams getOstreams() throws PersistException {

        if (outPool.size() == 0) {
            extendPool();
        }
        return (OutStreams)outPool.remove(0);
    }
    
    void returnIstreams(InStreams i) {
        inPool.add(i);
    }
    
    void returnOstreams(OutStreams o) {
        outPool.add(o);
    }
    
    public static ValueStore getInstance(){
            if (instance == null) {
                synchronized(ValueStore.class) {
                    if (instance == null) {
                        instance = new ValueStore();
                    }
                }
            }
            return instance;
        }    
    static List inPool;
    static List outPool;

    /**
     * @link
     * @shapeType PatternLink
     * @pattern Singleton
     * @supplierRole Singleton factory 
     */
    /*# private ValueStrore _valueStrore; */    
    private static ValueStore instance = null;
}


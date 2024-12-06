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
import java.util.HashMap;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Class that represents the log of transactional activity. Writes out entries
 * as transactions commit. Is responsible for 'rolling forward' the log in a
 * warm start after a termination without a shutdown.
 */
class TransactionLog {
	/**
	 * Write out a transaction log entry for the argument Persistent object and
	 * transaction. If the write succeeds, return the log object. if there is a
	 * failure (due to an IO problem, for instance), null is returned.
	 */
	synchronized LogEntry logEntry(Persistent po, Transaction tx) {
		PersistentLogEntry logged = new PersistentLogEntry(po, tx);

		try {
			logFileWriteObj.writeObject(logged);
			flusher.addChanged(logged);
			return logged;
		} catch (Exception e) {
			System.err.println("Failure to log PO update.  " + e);
			return null;
		}
	}

	/**
	 * replay checkpoints and logs, then start up.
	 */
	void warmStart(PersistentLibrary lib) {

		flusher.warmStart(lib);
		replay(lib);
		buildLogFile();
		flusher.startCheckpointer();
		flusher.logAvailable();
	}

	/**
	 * Close the current transaction log and replace it with an empty file.
	 */
	synchronized boolean rollLogfile() {

		closeLogFile();
		if (buildLogFile()) {
			flusher.logAvailable();
			rcmdRoll = false;
			return true;
		}
		return false;
	}

	boolean coldStart() {

		logLowSequence = 0;
		logHighSequence = 0;

		flusher.coldStart();

		if (logDirectory.exists() && logDirectory.isDirectory()) {

			if (destroyFiles(LOGFILENAME) && buildLogFile()) {
				setLogLowSequence();
				return true;
			}
		}
		return false;
	}

	/**
	 * Builds a new transaction log (destroying any previously existing one) in
	 * the appropriate directory and returns true. If the directory does not
	 * exist, does nothing and returns false.
	 */
	private synchronized boolean buildLogFile() {

		if (tm == null) { // not the ideal place to do this. Can't get it in
							// constructer
							// since the TransactionMgr hasn't finished
							// constructing.
			tm = TransactionMgr.getInstance();
		}

		if (logDirectory == null) {
			logDirectory = new File(logDirName);
		}

		if (logDirectory.exists()) {
			try {
				// destroyLogFile();
				log = new File(getFileName(LOGFILENAME, ++logHighSequence));
				logFile = new RandomAccessFile(log, "rw");
				logFileFD = logFile.getFD();
				logFileWrite = new FileOutputStream(logFileFD);
				logFileBuffered = new BufferedOutputStream(logFileWrite);
				logFileWriteObj = new ObjectOutputStream(logFileBuffered);
				logFileWriteObj.flush();
				if (logFileWrite == null || logFileBuffered == null || logFileWriteObj == null)
					System.err.println("Failed to build Transaction Log File.");
				return true;
			} catch (Exception e) {
				System.err.println("FAILED TO BUILD TRANSACTION LOG!!! \n" + e);
			}
		}
		return false;
	}

	/**
	 * Closes (but does not destroy) the current log file.
	 */
	synchronized void closeLogFile() {

		if (logFileWriteObj != null) {
			try {
				logFileWriteObj.close();
			} catch (IOException ioe) {
				System.err.println("Close problem: " + ioe);
			}
		}
	}

	/**
	 * Reads the log file, inserting any Persistent objects logged for committed
	 * transactions into the parameter PersistentLibrary. Returns true if any
	 * Persistent object was restored to the library, false otherwise.
	 */
	private boolean replay(PersistentLibrary lib) {
		boolean rolledForward = false;
		FileInputStream rp = null;
		LogEntry wkf;
		HashMap<Long, List<PersistentLogEntry>> restore = new HashMap<Long, List<PersistentLogEntry>>();
		List<PersistentLogEntry> tran;
		Long tid;
		BufferedInputStream bi = null;
		ObjectInputStream roi = null;

		File[] lf = getFiles(LOGFILENAME);

		for (int loop = 0; loop < lf.length; ++loop) {
			try {
				rp = new FileInputStream(lf[loop]);
				bi = new BufferedInputStream(rp);
				roi = new ObjectInputStream(bi);

				while ((wkf = (LogEntry) roi.readObject()) != null) {

					tid = new Long(wkf.getTranID());
					if (wkf instanceof EndTransactionEntry) { // this is a
																// commit marker
						if (restore.containsKey(tid)) {
							rolledForward = true;

							for (PersistentLogEntry ple : restore.get(tid)) {

								// will overwrite any previous value in the
								// library
								lib.addTo(Persistent.persistentFactory(ple));
								flusher.addChanged(ple);
							}
							lib.processDeletions((List<PersistentLogEntry>) restore.get(tid));
						}
						restore.remove(tid);
					} else { // this is an actual persistent update
						if (restore.containsKey(tid))
							tran = (List<PersistentLogEntry>) restore.get(tid);
						else {
							tran = new LinkedList<PersistentLogEntry>();
							restore.put(tid, tran);
						}
						tran.add((PersistentLogEntry) wkf);
					}
				}
			} catch (EOFException ok) {
			} catch (Exception e) {
				System.err.println(e);
			}
			if (rp != null)
				try {
					if (roi != null)
						roi.close();
					if (bi != null)
						bi.close();
					if (rp != null)
						rp.close();
				} catch (Exception e) {
				}
			// ensure that the checkpointer rolls its checkpoint data forwards.
			flusher.logAvailable();
		}

		logLowSequence = extractSequence(lf, LOGFILENAME, true);
		logHighSequence = extractSequence(lf, LOGFILENAME, false);

		return rolledForward;
	}

	/**
	 * Writes the log entry marking a committed transaction. Returns true if the
	 * log entry was written successfully, false otherwise.
	 */
	boolean logCommit(Transaction tx) {
		return (logEndTransaction(true, tx));
	}

	/**
	 * Writes the log entry marking a rolled-back transaction. Returns true if
	 * the log entry was written successfully, false otherwise.
	 */
	boolean logRollBack(Transaction tx) {
		// don't write end transaction marker for a rollback.
		// return (logEndTransaction(false, tx));
		return true;
	}

	/**
	 * Performs the actual writing of the special entry marking the end of a
	 * transaction (either commit or roll-back). Returns true if successful,
	 * false otherwise.
	 */
	private synchronized boolean logEndTransaction(boolean commit, Transaction tx) {

		++numCmts;

		try {
			if (commit) {
				logFileWriteObj.writeObject(new EndTransactionEntry(tx.getID()));
			}

			if (numCmts >= tm.getSyncFrequency()) {
				numCmts = 0;
				logFileWriteObj.flush();
				logFileFD.sync();

				try {
					long len = logFile.length();
					if (rcmdRoll == false && len > TransactionMgr.MAX_FILESIZE) {
						rcmdRoll = true;
					}
				} catch (IOException ioe) {
					System.out.println(ioe.getMessage());
				/* ?? don't care ?? */}
			}
		} catch (Exception e) {
			System.err.println("Failure to log End of Transaction " + commit + tx + e);
			return false;
		}
		return true;
	}

	void shutDown() {
		try {
			flusher.shutDown();
			closeLogFile();
			destroyFiles(LOGFILENAME);

		} catch (Exception e) {
			System.err.println("Problem closing log file: " + e);
		}
	}

	/**
	 * Reads the checkpoint directory to find all the checkpoint files. returns
	 * an array of file objects, sorted in ascending order of sequence number.
	 */
	File[] getFiles(String nameRoot) {

		File[] fileArray = logDirectory.listFiles(new FileFilter(nameRoot));

		if (fileArray == null)
			return null;

		// ensure that the files are sorted in the array by ascending sequence
		// of
		// filename

		Arrays.sort(fileArray);

		return fileArray;
	}

	/**
	 * Destroys the file with the supplied name root and sequence part that is
	 * >= lowSequence and <= highSequence.
	 */
	boolean destroyFile(String nameRoot, int sequence) {

		File victim = new File(getFileName(nameRoot, sequence));
		try {
			victim.delete();
		} catch (Exception e) {
			System.err.println("Unable to destroy file: " + e);
			return false;
		}
		return true;
	}

	/**
	 * Destroys all existing files with the supplied name root.
	 */
	boolean destroyFiles(String nameRoot) {
		// delete all the files in the Log Directory with the matching name
		// root.

		File[] filesInLib = getFiles(nameRoot);
		for (int x = 0; x < filesInLib.length; ++x) {
			try {
				filesInLib[x].delete();
			} catch (Exception e) {
				System.err.println("Unable to destroy file: " + e);
				return false;
			}
		}
		return true;
	}

	/**
	 * Build a file name with a sequence number suffix from the supplied root
	 * and sequence number
	 */
	public String getFileName(java.lang.String nameRoot, int sequence) {
		return (logDirName + File.separatorChar + nameRoot + mkSeqNoString(sequence));
	}

	/**
	 * Creates the sequence number suffix used in file names.
	 */
	String mkSeqNoString(int seqNo) {

		String num = Integer.toString(seqNo);
		if (num.length() >= zeroString.length()) {
			return num;
		}
		return (zeroString.substring(num.length()) + num);
	}

	public int extractSequence(java.io.File[] sequencedFiles, java.lang.String nameRoot, boolean low) {

		int rc = 0;
		int index;

		if (sequencedFiles != null && sequencedFiles.length > 0) {
			if (low) {
				index = 0;
			} else {
				index = sequencedFiles.length - 1;
			}
			String name = sequencedFiles[index].getName();
			int posn = name.lastIndexOf(nameRoot) + nameRoot.length();
			rc = Integer.parseInt(name.substring(posn));
		}
		return rc;
	}

	/**
	 * Getter for property logLowSequence.
	 * 
	 * @return Value of property logLowSequence.
	 */
	int getLogLowSequence() {
		return logLowSequence;
	}

	/**
	 * Setter for property logLowSequence.
	 * 
	 * @param logLowSequence
	 *            New value of property logLowSequence.
	 */
	void setLogLowSequence() {
		logLowSequence = extractSequence(getFiles(LOGFILENAME), LOGFILENAME, true);
	}

	/**
	 * Getter for property logHighSequence.
	 * 
	 * @return Value of property logHighSequence.
	 */
	int getLogHighSequence() {
		return logHighSequence;
	}

	/**
	 * Getter for property rcmdRoll.
	 * 
	 * @return Value of property rcmdRoll.
	 */
	public boolean recommendCkp() {
		return rcmdRoll;
	}

	/**
	 * java.io.FileFilter used to select checkpoint files froma directory's
	 * contents.
	 */
	class FileFilter implements java.io.FileFilter {

		public FileFilter(java.lang.String nameRoot) {

			this.nameRoot = nameRoot;
		}

		public boolean accept(java.io.File file) {

			if ((file.getName()).startsWith(nameRoot)) {
				return true;
			} else {
				return false;
			}
		}

		String nameRoot;
	}

	/**
	 * Constructor for transaction log in the directory specified by the full
	 * path name string parameter.
	 */
	TransactionLog(String logDir) {
		logDirName = logDir;
		logDirectory = new File(logDirName);
		rcmdRoll = false;
		flusher = new Checkpointer(this);
	}

	static final String LOGFILENAME = "TransactionLog";

	private int numCmts = 0;

	private boolean rcmdRoll;
	private TransactionMgr tm;
	private int logLowSequence;
	private int logHighSequence;
	private String logDirName;
	private File logDirectory;
	private File log;
	private RandomAccessFile logFile;
	private FileOutputStream logFileWrite;
	private FileDescriptor logFileFD;
	private BufferedOutputStream logFileBuffered;
	private ObjectOutputStream logFileWriteObj;
	private Checkpointer flusher;

	private static final String zeroString = "0000000000";

}

package no.hvl.dat110.util;

/**
 * @author tdoy
 * dat110 - project 3
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

public class FileManager {

	private BigInteger[] replicafiles; // array stores replicated files for distribution to matching nodes
	private int numReplicas; // let's assume each node manages nfiles- can be changed from the constructor
	private NodeInterface chordnode;
	private String filepath; // absolute filepath
	private String filename; // only filename without path and extension
	private BigInteger hash;
	private byte[] bytesOfFile;
	private String sizeOfByte;

	private Set<Message> activeNodesforFile = null;

	public FileManager(NodeInterface chordnode) throws RemoteException {
		this.chordnode = chordnode;
	}

	public FileManager(NodeInterface chordnode, int N) throws RemoteException {
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}

	public FileManager(NodeInterface chordnode, String filepath, int N) throws RemoteException {
		this.filepath = filepath;
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}

	/**
	 * Creates replicas of a file
	 */
	public void createReplicaFiles() {

		for (int i = 0; i < Util.numReplicas; i++) {
			String replica = filename + i;
			replicafiles[i] = Hash.hashOf(replica);
			System.out.println(replica + " gives hashOf " + replicafiles[i]);
		}

	}

	/**
	 * Given a filename, makes replicas and distributes them to all active peers
	 * such that pred < replica <= peer
	 *
	 * @param bytesOfFile
	 * @throws RemoteException
	 */
	public int distributeReplicastoPeers() throws RemoteException {

		int counter = 0;

		// Task2: assign a replica as the primary for this file. Hint, see the slide
		// (project 3) on Canvas
		Random rnd = new Random();
		int index = rnd.nextInt(Util.numReplicas); // eller -1? nextInt er ikke inklusiv den oppgitte verdien

		// create replicas of the filename
		createReplicaFiles();

		// iterate over the replicas
		for (int i = 0; i < replicafiles.length; i++) {

			BigInteger replica = replicafiles[i];

			// for each replica, find its successor by performing findSuccessor(replica)
			NodeInterface succ = chordnode.findSuccessor(replica);

			// call the addKey on the successor and add the replica
			succ.addKey(replica);

			// call the saveFileContent() on the successor
			if (i == index) {
				succ.saveFileContent(filename, replica, bytesOfFile, true);
			} else {
				succ.saveFileContent(filename, replica, bytesOfFile, false);
			}

			// increment counter
			counter++;
		}

		return counter;
	}

	/**
	 * Finds all the peers that hold a copy of this file based on the filename
	 *
	 * @param filename
	 * @return list of active nodes having the replicas of this file
	 * @throws RemoteException
	 */
	public Set<Message> requestActiveNodesForFile(String filename) throws RemoteException {

		this.filename = filename;
		Set<Message> succinfo = new HashSet<Message>();
		// Task: Given a filename, find all the peers that hold a copy of this file

		// generate the N replicas from the filename by calling createReplicaFiles()
		createReplicaFiles();

		// it means, iterate over the replicas of the file
		for (int i = 0; i < replicafiles.length; i++) {
			// for each replica, do findSuccessor(replica) that returns successor s.
			BigInteger replica = replicafiles[i];
			NodeInterface succ = chordnode.findSuccessor(replica);

			// get the metadata (Message) of the replica from the successor, s (i.e. active
			// peer) of the file
			Message metadata = succ.getFilesMetadata(replica);

			// save the metadata in the set succinfo.
			succinfo.add(metadata);
		}

		this.activeNodesforFile = succinfo;

		return succinfo;
	}

	/**
	 * Find the primary server - Remote-Write Protocol
	 *
	 * @return
	 */
	public NodeInterface findPrimaryOfItem() {

		// Task: Given all the active peers of a file (activeNodesforFile()), find which
		// is holding the primary copy
		NodeInterface primary = null;

		// iterate over the activeNodesforFile
		for (Message activePeer : activeNodesforFile) {

			// for each active peer (saved as Message)
			// use the primaryServer boolean variable contained in the Message class to
			// check if it is the primary or not
			if (activePeer.isPrimaryServer()) {
				primary = Util.getProcessStub(activePeer.getNodeIP(), activePeer.getPort());
			}
		}
		// return the primary
		return primary;
	}

	/**
	 * Read the content of a file and return the bytes
	 *
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	public void readFile() throws IOException, NoSuchAlgorithmException {

		File f = new File(filepath);

		byte[] bytesOfFile = new byte[(int) f.length()];

		FileInputStream fis = new FileInputStream(f);

		fis.read(bytesOfFile);
		fis.close();

		// set the values
		filename = f.getName().replace(".txt", "");
		hash = Hash.hashOf(filename);
		this.bytesOfFile = bytesOfFile;
		double size = (double) bytesOfFile.length / 1000;
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		sizeOfByte = nf.format(size);

		System.out.println("filename=" + filename + " size=" + sizeOfByte);

	}

	public void printActivePeers() {

		activeNodesforFile.forEach(m -> {
			String peer = m.getNodeIP();
			String id = m.getNodeID().toString();
			String name = m.getNameOfFile();
			String hash = m.getHashOfFile().toString();
			int size = m.getBytesOfFile().length;

			System.out.println(
					peer + ": ID = " + id + " | filename = " + name + " | HashOfFile = " + hash + " | size =" + size);

		});
	}

	/**
	 * @return the numReplicas
	 */
	public int getNumReplicas() {
		return numReplicas;
	}

	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}

	/**
	 * @return the hash
	 */
	public BigInteger getHash() {
		return hash;
	}

	/**
	 * @param hash the hash to set
	 */
	public void setHash(BigInteger hash) {
		this.hash = hash;
	}

	/**
	 * @return the bytesOfFile
	 */
	public byte[] getBytesOfFile() {
		return bytesOfFile;
	}

	/**
	 * @param bytesOfFile the bytesOfFile to set
	 */
	public void setBytesOfFile(byte[] bytesOfFile) {
		this.bytesOfFile = bytesOfFile;
	}

	/**
	 * @return the size
	 */
	public String getSizeOfByte() {
		return sizeOfByte;
	}

	/**
	 * @param size the size to set
	 */
	public void setSizeOfByte(String sizeOfByte) {
		this.sizeOfByte = sizeOfByte;
	}

	/**
	 * @return the chordnode
	 */
	public NodeInterface getChordnode() {
		return chordnode;
	}

	/**
	 * @return the activeNodesforFile
	 */
	public Set<Message> getActiveNodesforFile() {
		return activeNodesforFile;
	}

	/**
	 * @return the replicafiles
	 */
	public BigInteger[] getReplicafiles() {
		return replicafiles;
	}

	/**
	 * @param filepath the filepath to set
	 */
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}
}

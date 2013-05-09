package distributed.project1.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

//import distributed.project1.server.Server;

/* syncclient/Client.java
 * 
 * Authors: Erick Gaspar and Nasir Uddin
 * 
 * Description: A basic TCP client setup built on top of the
 * Filesync protocol. Allows negotiation of data flow and block size
 * upon connecting to the server via user input. 
 * 
 * Calls InstructionThread when it is designated as the sender. 
 * 
 * Code structure based on Aaron Harwood's SyncTest
 * 
 */

public class Client {

	private final int DEFAULT_PORT = 7654;
	private final int MAX_BLOCK_SIZE = 40000;

	private DataOutputStream outToServer; // output to server
	private DataInputStream inFromServer; // input from server

	private Socket clientSocket = null; // connection to server

	private SynchronisedFile file = null;

	private String protocol = "";
	private Integer blockSize = 0;
	private Instruction receivedInst = null;

	public static void main(String[] args) {

		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(
				System.in));

		Client applicationServer = new Client(); // create Client
		applicationServer.startClientMethod(args, inFromUser); // run Client
																// Method

	}
	
	/*
	 * clientMethod will connect to the server and initiate file sync
	 */
	public void startClientMethod(String[] args, BufferedReader inFromUser) {
		try {

			connectServer(args); // create a Socket to make connection

			getStreams(); // get the input and output streams

			while (!protocol.equals("R") && !protocol.equals("S")) {
				protocol = inFromUser.readLine();
			}

			outToServer.writeUTF(protocol);

			// Blocksize
			System.out.println(inFromServer.readUTF());

			while (blockSize <= 0 || blockSize > MAX_BLOCK_SIZE) {
				blockSize = Integer.parseInt(inFromUser.readLine());
			}
			outToServer.writeUTF(blockSize.toString());

			// Start sync
			System.out.println(inFromServer.readUTF());

			// Open file with blocksize
			file = new SynchronisedFile(args[1], blockSize);

		} // end try
		catch (IOException e) {
			e.printStackTrace();
		} // end catch

		if (protocol.equals("S")) {
			send();
		} else if (protocol.equals("R")) {
			receive();
		}
	} // end startClientMethod

	// connect to server
	private void connectServer(String[] args) throws IOException {

		// create Socket to make connection to server
		clientSocket = new Socket(args[0], DEFAULT_PORT);
		// display connection information
		System.out.println("Connected to: "
				+ clientSocket.getInetAddress().getHostName());
	} // end method connectServer

	// get streams to send and receive data
	private void getStreams() throws IOException {
		// set up output stream for objects
		outToServer = new DataOutputStream(clientSocket.getOutputStream());

		// set up input stream for objects
		inFromServer = new DataInputStream(clientSocket.getInputStream());

		// "Sending or Receiving?" must be replied with S or R
		System.out.println(inFromServer.readUTF());
	} // end getSteam method

	/*
	 * This method will send data blocks
	 */
	private void send() {

		Thread stt = new Thread(new InstructionThread(file, clientSocket));
		stt.start();

		/*
		 * Continue forever, checking the fromFile every 5 seconds.
		 */
		while (true) {
			try {
				// skip if the file is not modified
				System.err
						.println("SynchTest: calling fromFile.CheckFileState()");
				file.CheckFileState();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	} // end of send method

	/*
	 * This method will receive instructions
	 */
	private void receive() {

		//
		// inner infinite loop for synchronization proper
		//
		InstructionFactory instFact = new InstructionFactory();

		while (!clientSocket.isClosed()) {

			try {
				// wait for instruction
				receivedInst = instFact.FromJSON(inFromServer.readUTF());

				// The Server processes the instruction
				file.ProcessInstruction(receivedInst);
				outToServer.writeUTF("Y");
			} catch (SocketException e) {
				System.out.println("Client closed connection");
				break;
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1); // just die at the first sign of
									// trouble
			} catch (BlockUnavailableException e) {
				// The server does not have the bytes referred to by the
				// block
				// hash.
				try {
					/*
					 * At this point the Server needs to send a request back to
					 * the Client to obtain the actual bytes of the block.
					 */

					outToServer.writeUTF("N");

					// network delay

					/*
					 * Server receives the NewBlock instruction.
					 */
					receivedInst = instFact.FromJSON(inFromServer.readUTF());

					file.ProcessInstruction(receivedInst);
				} catch (IOException e1) {
					e1.printStackTrace();
					System.exit(-1);
				} catch (BlockUnavailableException e1) {
					assert (false); // a NewBlockInstruction can never
									// throw
									// this exception
				}
			}
		}
	} // end of receive method
}

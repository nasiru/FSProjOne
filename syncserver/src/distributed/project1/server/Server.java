package distributed.project1.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/* syncserver/Server.java
 * 
 * Authors: Erick Gaspar and Nasir Uddin
 * 
 * Description: A basic TCP server setup built on top of the
 * Filesync protocol. Allows negotiation of data flow and block size
 * with its clients. Calls InstructionThread when it is designated as
 * the sender. 
 * 
 * Code structure based on Aaron Harwood's SyncTest
 * 
 */

public class Server {

	private final int DEFAULT_PORT = 7654;
	private final int MAX_BLOCK_SIZE = 40000;

	private ServerSocket serverSocket = null; // server socket
	private Socket connectionSocket = null; // connection to client

	private SynchronisedFile file = null;

	private DataInputStream inFromClient = null;
	private DataOutputStream outToClient = null;

	private String protocol = null;
	private String blockSize = null;

	private Instruction receivedInst = null;

	public boolean isThreadAlive = true;

	public static void main(String[] args) {

		Server applicationServer = new Server(); // create Server
		applicationServer.serverMethod(args); // run Server

	}

	public void serverMethod(String[] args) {
		try { // set up server to receive connections; process connections

			serverSocket = new ServerSocket(DEFAULT_PORT); // create
															// ServerSocket

			System.out.println("Listening to port: "
					+ serverSocket.getLocalPort());

			while (true) {
				waitForConnection(); // wait for a connection
				System.out.println("Connected to client");

				processSteams(); // process input & output streams

				startProcessing(args); // process the connection to sync files
			} // end while
		} // end try

		catch (IOException e) {
			e.printStackTrace();
		}
	}

	// wait for connection to arrive, then display connection info
	private void waitForConnection() throws IOException {
		System.out.println("Waiting for connection\n");
		connectionSocket = serverSocket.accept(); // allow server to accept
													// connection
		System.out.println("Connection received from: "
				+ connectionSocket.getInetAddress().getHostName());
	} // end method waitForConnection

	// get streams to send and receive data
	private void processSteams() throws IOException {

		outToClient = new DataOutputStream(connectionSocket.getOutputStream());
		outToClient.writeUTF("(S) Sending or (R) Receiving?");
		outToClient.writeUTF("Enter block size (1-" + MAX_BLOCK_SIZE + ") : ");

		inFromClient = new DataInputStream(connectionSocket.getInputStream());
		protocol = inFromClient.readUTF();

		blockSize = inFromClient.readUTF();

		System.out.println("Received: " + protocol + " and " + blockSize);

		outToClient.writeUTF("Got it. Starting synchronization...");
		outToClient.flush(); // flush output buffer to send header information
	} // end method processSteams

	// process connection with client
	private void startProcessing(String[] args) throws IOException {
		// Open file with blocksize
		file = new SynchronisedFile(args[0], Integer.parseInt(blockSize));

		isThreadAlive = true;

		if (protocol.equals("S")) {
			receive();
		} else if (protocol.equals("R")) {
			send();
		}
	} // end process connection method

	// send method will start a thread
	private void send() {

		Thread stt = new Thread(new InstructionThread(file, connectionSocket));
		stt.start();

		/*
		 * Continue forever, checking the fromFile every 5 seconds.
		 */
		while (true) {
			try {
				// terminate if client closed connection
				if (!isThreadAlive) {
					break;
				}

				// skip if the file is not modified
				System.err
						.println("SynchTest: calling fromFile.CheckFileState()");
				file.CheckFileState();

			} // end try
			catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			} // end catch

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	// receive method will receive instructions
	private void receive() {

		//
		// inner infinite loop for synchronization proper
		//
		InstructionFactory instFact = new InstructionFactory();

		while (true) {

			try {
				// wait for instruction
				receivedInst = instFact.FromJSON(inFromClient.readUTF());

				// The Server processes the instruction
				file.ProcessInstruction(receivedInst);
				outToClient.writeUTF("Y");
			} catch (SocketException e) {
				System.out.println("Client closed connection");
				break;
			} catch (EOFException e) {
				// catch if client disconnected and go back to listening
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

					outToClient.writeUTF("N");

					// network delay

					/*
					 * Server receives the NewBlock instruction.
					 */
					receivedInst = instFact.FromJSON(inFromClient.readUTF());

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
	} // end receive method

	// close streams and socket
	private void closeConnection() {
		System.out.println("\nTerminating connection\n");

		try {
			outToClient.close(); // close output stream
			inFromClient.close(); // close input stream
			connectionSocket.close(); // close socket
		} // end try
		catch (IOException ioException) {
			ioException.printStackTrace();
		} // end catch
	} // end method closeConnection

}

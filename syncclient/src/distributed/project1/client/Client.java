package distributed.project1.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

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

	static final int DEFAULT_PORT = 7654;
	static final int MAX_BLOCK_SIZE = 40000;

	static DataOutputStream outToServer;
	static DataInputStream inFromServer;

	static Socket clientSocket = null;

	static SynchronisedFile file = null;

	static String protocol = "";
	static Integer blockSize = 0;

	static Instruction receivedInst = null;

	public static void main(String[] args) {

		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(
				System.in));

		try {

			clientSocket = new Socket(args[0], DEFAULT_PORT);

			outToServer = new DataOutputStream(clientSocket.getOutputStream());

			inFromServer = new DataInputStream(clientSocket.getInputStream());

			// "Sending or Receiving?" must be replied with S or R
			System.out.println(inFromServer.readUTF());

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

		} catch (IOException e) {
			e.printStackTrace();
		}

		if (protocol.equals("S")) {
			send();
		} else if (protocol.equals("R")) {
			receive();
		}

	}

	private static void send() {

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
	}

	private static void receive() {

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
	}
}

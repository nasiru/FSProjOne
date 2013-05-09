package distributed.project2.cipher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

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

	static final int DEFAULT_PORT = 7654;
	static final int MAX_CLIENTS = 10;
	static final int MAX_BLOCK_SIZE = 40000;

	static ServerSocket serverSocket = null;
	static Socket connectionSocket = null;

	static DataInputStream inFromClient = null;
	static DataOutputStream outToClient = null;

	static Thread[] clients = new Thread[MAX_CLIENTS];

	public static void main(String[] args) {

		String protocol = null;
		String blockSize = null;
		SynchronisedFile file = null;

		try {
			serverSocket = new ServerSocket(DEFAULT_PORT);

			System.out.println("Listening to port: "
					+ serverSocket.getLocalPort());

			int i = 0;

			while (true) {
				connectionSocket = serverSocket.accept();

				outToClient = new DataOutputStream(
						connectionSocket.getOutputStream());
				inFromClient = new DataInputStream(
						connectionSocket.getInputStream());

				for (i = 0; i < MAX_CLIENTS; i++) {
					if (clients[i] == null) {
						System.out.println("Connected to client");

						outToClient.writeUTF("(S) Sending or (R) Receiving?");

						protocol = inFromClient.readUTF();

						outToClient.writeUTF("Enter block size (1-"
								+ MAX_BLOCK_SIZE + ") : ");
						blockSize = inFromClient.readUTF();

						System.out.println("Received: " + protocol + " and "
								+ blockSize);

						outToClient
								.writeUTF("Got it. Starting synchronization...");

						// Open file with blocksize
						file = new SynchronisedFile(args[0],
								Integer.parseInt(blockSize));

						clients[i] = new Thread(new InstructionThread(file,
								connectionSocket, protocol));
						clients[i].start();
						break;

					}
				}

				if (i == MAX_CLIENTS) {
					outToClient = new DataOutputStream(
							connectionSocket.getOutputStream());
					outToClient.writeUTF("Max clients reached.");
					outToClient.close();
					outToClient.close();
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}

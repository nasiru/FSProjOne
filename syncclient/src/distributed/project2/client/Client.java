package distributed.project2.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

import org.bouncycastle.util.encoders.Base64;

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

	static ObjectOutputStream outToServer;
	static ObjectInputStream inFromServer;

	static Socket clientSocket = null;

	static SynchronisedFile file = null;

	static String protocol = "";
	static Integer blockSize = 0;

	static Instruction receivedInst = null;

	// server public key
	static PublicKey pk = null;

	static byte[] password = null;
	static byte[] symmetricKey = null;

	public static void main(String[] args) {

		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(
				System.in));

		try {

			clientSocket = new Socket(args[0], DEFAULT_PORT);
			outToServer = new ObjectOutputStream(clientSocket.getOutputStream());
			inFromServer = new ObjectInputStream(clientSocket.getInputStream());

			// Receive server public key
			pk = (PublicKey) inFromServer.readObject();

			System.out.print("Enter server password: ");

			// get encrypted password and random symmetric key for this session
			password = HybridCipher.init(pk, inFromUser.readLine());
			symmetricKey = HybridCipher.getEncryptedKey(pk);

			// send server the encrypted key, then the password.
			outToServer.writeObject(symmetricKey);
			System.out.println("key sent");
			outToServer.writeObject(password);
			System.out.println("pass sent");

			// If the server accepts the key and password, the program continues

			// "Sending or Receiving?" must be replied with S or R
			System.out.println(new String(HybridCipher
					.decrypt((byte[]) inFromServer.readObject())));

			while (!protocol.equals("R") && !protocol.equals("S")) {
				protocol = inFromUser.readLine();
			}

			// encrypt protocol then send to server
			outToServer.writeObject(HybridCipher.encrypt(protocol.getBytes()));

			// Blocksize
			System.out.println(new String(HybridCipher
					.decrypt((byte[]) inFromServer.readObject())));

			while (blockSize <= 0 || blockSize > MAX_BLOCK_SIZE) {
				blockSize = Integer.parseInt(inFromUser.readLine());
			}

			outToServer.writeObject(HybridCipher.encrypt(blockSize.toString()
					.getBytes()));

			// Start sync
			System.out.println(inFromServer.readObject());

			// Open file with blocksize
			file = new SynchronisedFile(args[1], blockSize);

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (GeneralSecurityException e) {
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
				receivedInst = instFact.FromJSON(HybridCipher.decrypt(
						inFromServer.readUTF().getBytes()).toString());

				// The Server processes the instruction
				file.ProcessInstruction(receivedInst);

				outToServer.writeUTF(new String(Base64.encode(HybridCipher
						.encrypt("Y".getBytes()))));

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

					outToServer.writeUTF(new String(Base64.encode(HybridCipher
							.encrypt("N".getBytes()))));

					// network delay

					/*
					 * Server receives the NewBlock instruction.
					 */
					receivedInst = instFact.FromJSON(HybridCipher.decrypt(
							inFromServer.readUTF().getBytes()).toString());

					file.ProcessInstruction(receivedInst);
				} catch (IOException e1) {
					e1.printStackTrace();
					System.exit(-1);
				} catch (BlockUnavailableException e1) {
					assert (false); // a NewBlockInstruction can never
									// throw
									// this exception
				} catch (GeneralSecurityException e1) {
					e.printStackTrace();
				}
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			}
		}
	}
}

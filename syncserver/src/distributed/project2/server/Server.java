package distributed.project2.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import javax.crypto.SecretKey;

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

	static ObjectInputStream inFromClient = null;
	static ObjectOutputStream outToClient = null;

	static Thread[] clients = new Thread[MAX_CLIENTS];

	static String serverPass = "";

	public static void main(String[] args) {

		String protocol = null;
		String blockSize = null;
		SynchronisedFile file = null;
		byte[] message = null;

		// generate keypair
		KeyPair keyPair = HybridCipher.init();

		try {
			serverSocket = new ServerSocket(DEFAULT_PORT);

			System.out.print("Set server password: ");
			serverPass = new BufferedReader(new InputStreamReader(System.in))
					.readLine();

		} catch (IOException e1) {
			e1.printStackTrace();
		}

		System.out.println("Listening to port: " + serverSocket.getLocalPort());

		int i = 0;

		while (true) {

			try {
				connectionSocket = serverSocket.accept();

				outToClient = new ObjectOutputStream(
						connectionSocket.getOutputStream());
				inFromClient = new ObjectInputStream(
						connectionSocket.getInputStream());

				for (i = 0; i < MAX_CLIENTS; i++) {
					if (clients[i] == null) {
						System.out.println("Connected to client");

						System.out.println("Sending public key");
						outToClient.writeObject(keyPair.getPublic());

						// grab encrypted data from client
						byte[] symmetricKey = (byte[]) inFromClient
								.readObject();

						System.out.println("got key");

						byte[] password = (byte[]) inFromClient.readObject();

						System.out.println("got pass");

						// decrypt symmetric key using private key
						SecretKey sk = HybridCipher.decryptKey(symmetricKey,
								keyPair);

						// use decoded key to decrypt password and verify
						if (HybridCipher.verifyPassword(password, sk,
								serverPass)) {

							try {
								// encrypt send/receive question
								message = HybridCipher.encrypt(new String(
										"(S) Sending or (R) Receiving?")
										.getBytes(), sk);

								outToClient.writeObject(message);

								// decrypt reply
								protocol = new String(HybridCipher.decrypt(
										(byte[]) inFromClient.readObject(), sk));

								// encrypt block size question
								message = HybridCipher.encrypt(
										("Enter block size (1-"
												+ MAX_BLOCK_SIZE + ") : ")
												.getBytes(), sk);
								outToClient.writeObject(message);

								// decrypt reply
								blockSize = new String(HybridCipher.decrypt(
										(byte[]) inFromClient.readObject(), sk));

								System.out.println("Received: " + protocol
										+ " and " + blockSize);

							} catch (GeneralSecurityException e) {
								e.printStackTrace();
							}

							outToClient
									.writeObject("Got it. Starting synchronization...");

							// Open file with blocksize
							file = new SynchronisedFile(args[0],
									Integer.parseInt(blockSize));

							clients[i] = new Thread(new InstructionThread(file,
									connectionSocket, protocol, sk));
							clients[i].start();
							break;
						} else {
							outToClient
									.writeUTF("Incorrect password, disconnecting.");
							outToClient.close();
							inFromClient.close();
							connectionSocket.close();
						}

					}
				}

				if (i == MAX_CLIENTS) {
					outToClient.writeUTF("Max clients reached.");
					outToClient.close();
					inFromClient.close();
				}
			} catch (SocketException e) {

			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
}

package distributed.project2.server;

import java.io.BufferedReader;
import java.io.Console;
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
 * with its clients.
 * 
 * Project 2 extends this by adding encryption protocols on top of
 * plaintext messages using the BouncyCastle Security Provider.
 * Multiple clients are now possible via multi-threading.
 * 
 * Calls InstructionThread when it is designated as the sender. 
 * HybridCipher handles the encryption layer.
 * 
 * Calls InstructionThread when it is designated as the sender. 
 * 
 * No arguments are needed for it to run.
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

			// mask password (returns null on Eclipse, so a fallback is placed)
			Console con = System.console();
			if (con != null) {
				serverPass = new String(con.readPassword());
			} else {
				serverPass = new BufferedReader(
						new InputStreamReader(System.in)).readLine();
			}
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

						byte[] symmetricKey;
						Object response = inFromClient.readObject();

						// if length = 1, then client needs the public key
						if (response.getClass().equals(Character.class)) {
							System.out.println("Sending public key");
							outToClient.writeObject(keyPair.getPublic());

							// grab encrypted data from client
							symmetricKey = (byte[]) inFromClient.readObject();
						} else {
							System.out
									.println("Client has key, not sending public key");
							symmetricKey = (byte[]) response;
						}

						System.out.println("got key");

						byte[] password = (byte[]) inFromClient.readObject();

						System.out.println("got pass");

						byte[] filename = (byte[]) inFromClient.readObject();

						System.out.println("got filename");

						// decrypt symmetric key using private key
						SecretKey sk = HybridCipher.decryptKey(symmetricKey,
								keyPair);

						// use decoded key to decrypt password and verify
						if (HybridCipher.verifyPassword(password, sk,
								serverPass)) {

							try {
								// encrypt "waiting" message
								message = HybridCipher
										.encrypt(
												new String(
														"Waiting for synchronization parameters...")
														.getBytes(), sk);

								outToClient.writeObject(message);

								// decrypt the combined parameters then parse it
								String params = new String(
										HybridCipher.decrypt(
												(byte[]) inFromClient
														.readObject(), sk));

								protocol = params.charAt(0) + "";
								blockSize = params.substring(1);

								System.out.println("Received: " + protocol
										+ " and " + blockSize);

							} catch (GeneralSecurityException e) {
								e.printStackTrace();
							}

							outToClient
									.writeObject("Got it. Starting synchronization...");

							// Open file with blocksize
							file = new SynchronisedFile(new String(
									HybridCipher.decrypt(filename, sk)),
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
				e1.printStackTrace();
			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			}
		}
	}
}

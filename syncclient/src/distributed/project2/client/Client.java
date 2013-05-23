package distributed.project2.client;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
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

	// asymmetric algorithms used
		public static String asymKeyAlgorithm = "RSA";
		public static String asymAlgorithm = "RSA/ECB/OAEPWithMD5AndMGF1Padding";
		public static int asymKeyAlgorithmStrength = 1024;

		// symmetric algorithms used
		public static String symKeyAlgorithm = "RIJNDAEL";
		public static String symAlgorithm = "RIJNDAEL";
		public static int symAlgorithmStrength = 256;
	
	
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

			// argument format: address filename S|R 1-40000
			
			// check for proper args
			if (args[2].equals("S") || args[2].equals("R")) {
				protocol = args[2];
			} else {
				System.out.println("Required arguments: address S|R 1-40000");
				System.exit(-1);
			}
			
			if (Integer.parseInt(args[3]) > 0 && Integer.parseInt(args[3]) < 40001) {
				blockSize = Integer.parseInt(args[3]);
			} else {
				System.out.println("Required arguments: address S|R 1-40000");
				System.exit(-1);
			}
			
			clientSocket = new Socket(args[0], DEFAULT_PORT);
			outToServer = new ObjectOutputStream(clientSocket.getOutputStream());
			inFromServer = new ObjectInputStream(clientSocket.getInputStream());

			File pubfile = new File("keys/" + args[0]);
			
			if (pubfile.exists()) {
				Security.addProvider(new BouncyCastleProvider());
				
				System.out.println("Found server in keys dir, reusing pub key...");
				
				// read public key from file
				FileInputStream pubin = new FileInputStream(pubfile);
				
				byte[] pubbytes = new byte[(int) pubfile.length()];

				pubin.read(pubbytes);
				pubin.close();
				
				// load both
				KeyFactory keyFactory = KeyFactory.getInstance(
						asymKeyAlgorithm, "BC");
				
				X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(pubbytes);
				PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
				
				pk = publicKey;
			} else {
			
				System.out.println("Pub key for this server not found, requesting...");
				
				// don't have the key, send any 1-char response to request the key
				outToServer.writeObject('.');
				
				// Receive server public key
				pk = (PublicKey) inFromServer.readObject();
				
				// save public key
				pubfile.createNewFile();
				FileOutputStream pubout = new FileOutputStream(pubfile);
				X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(pk.getEncoded());
				pubout.write(x509EncodedKeySpec.getEncoded());
				pubout.close();
				
			}
			
			System.out.print("Enter server password: ");

			// get encrypted password and random symmetric key for this session
			// mask password (returns null on Eclipse, so a fallback is placed)
						Console con = System.console();
						if (con != null) {
							password = HybridCipher.init(pk, con.readPassword().toString());
						} else {
							password = HybridCipher.init(pk, inFromUser.readLine());
						}
						
			symmetricKey = HybridCipher.getEncryptedKey(pk);

			// send server the encrypted key, then the password.
			outToServer.writeObject(symmetricKey);
			System.out.println("key sent");
			outToServer.writeObject(password);
			System.out.println("pass sent");

			// If the server accepts the key and password, the program continues

			// Print waiting notification
			System.out.println(new String(HybridCipher
					.decrypt((byte[]) inFromServer.readObject())));

			// combine protocol and block size into one string, encrypt it and send
			outToServer.writeObject(HybridCipher.encrypt((protocol + blockSize.toString()).getBytes()));

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

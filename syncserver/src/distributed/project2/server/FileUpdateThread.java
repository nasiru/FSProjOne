package distributed.project2.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

/* FileUpdateThread.java
 * 
 * Authors: Erick Gaspar and Nasir Uddin
 * 
 * Description: Handles the received JSON messages containing
 * synchronization instructions. Follows a request-reply protocol
 * 'Y' or 'N' for success or for BlockUnavailableException
 * respectively.
 * 
 * Includes a handler for SocketException to prevent crashing upon
 * unclean client disconnections.
 * 
 * Project 2 modification: This is spawned from InstructionThread in order to process
 * the instruction queue separately, as the file needs to be checked every so often.
 * 
 * Code structure based on Aaron Harwood's SyncTestThread
 * 
 */
public class FileUpdateThread implements Runnable {

	SynchronisedFile file = null;

	ObjectOutputStream out;
	ObjectInputStream in;

	SecretKey symmetricKey;

	Boolean isAlive;

	FileUpdateThread(SynchronisedFile f, ObjectOutputStream out,
			ObjectInputStream in, SecretKey sk) {
		file = f;
		symmetricKey = sk;
		this.out = out;
		this.in = in;
		isAlive = true;
	}

	public void run() {

		Instruction inst;

		// The Client reads instructions to send to the Server
		while ((inst = file.NextInstruction()) != null) {

			String msg = inst.ToJSON();
			String response = null;

			try {
				/*
				 * The Server sends the msg to the Client.
				 */
				System.err.println("Sending: " + msg);

				out.writeObject(HybridCipher.encrypt(msg.getBytes(),
						symmetricKey));

				/*
				 * The Server receives the instruction here.
				 */
				response = new String(HybridCipher.decrypt(
						(byte[]) in.readObject(), symmetricKey));

				if (response.equals("N")) {
					/*
					 * Server upgrades the CopyBlock to a NewBlock instruction
					 * and sends it.
					 */

					Instruction upgraded = new NewBlockInstruction(
							(CopyBlockInstruction) inst);
					String msg2 = upgraded.ToJSON();

					System.err.println("Sending: " + msg2);
					out.writeObject(HybridCipher.encrypt(msg2.getBytes(),
							symmetricKey));
				} else if (response.equals("Y")) { // success

					/*
					 * If using a synchronous RequestReply protocol, the server
					 * can now acknowledge that the block was correctly
					 * received, and the next instruction can be sent.
					 */

					// We do nothing here.

					/*
					 * Client receives acknowledgement and moves on to process
					 * next instruction.
					 */

				}

			} catch (SocketException e) {
				isAlive = false;
				break;
			} catch (IOException e) {

				e.printStackTrace();
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

		} // get next instruction loop forever

	}

}

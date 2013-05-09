package distributed.project2.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;

/* InstructionThread.java
 * 
 * Authors: Erick Gaspar and Nasir Uddin
 * 
 * Description: Handles the received JSON messages containing
 * synchronization instructions. Follows a request-reply protocol
 * 'Y' or 'N' for success or for BlockUnavailableException
 * respectively.
 * 
 * Code structure based on Aaron Harwood's SyncTestThread
 * 
 */

public class InstructionThread implements Runnable {

	SynchronisedFile file;
	Socket socket;

	ObjectOutputStream out;
	ObjectInputStream in;

	InstructionThread(SynchronisedFile f, Socket s) {
		file = f;
		socket = s;

		try {
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public void run() {
		Instruction inst;

		// The Client reads instructions to send to the Server
		while ((inst = file.NextInstruction()) != null) {

			String msg = inst.ToJSON();
			String response = null;

			try {
				/*
				 * The Client sends the msg to the Server.
				 */
				System.err.println("Sending: " + msg);

				out.writeObject(HybridCipher.encrypt(msg.getBytes()));

				/*
				 * The Client receives the instruction here.
				 */
				response = new String(HybridCipher.decrypt((byte[]) in
						.readObject()));

				if (response.equals("N")) {
					/*
					 * Client upgrades the CopyBlock to a NewBlock instruction
					 * and sends it.
					 */
					Instruction upgraded = new NewBlockInstruction(
							(CopyBlockInstruction) inst);
					String msg2 = upgraded.ToJSON();

					System.err.println("Sending: " + msg2);
					out.writeObject(HybridCipher.encrypt(msg2.getBytes()));

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

			} catch (IOException e) {

				e.printStackTrace();
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} // get next instruction loop forever
	}
}

package distributed.project1.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

/* InstructionThread.java
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
 * Code structure based on Aaron Harwood's SyncTestThread
 * 
 */

public class InstructionThread implements Runnable {

	SynchronisedFile file;
	Socket socket;

	DataOutputStream out;
	DataInputStream in;

	InstructionThread(SynchronisedFile f, Socket s) {
		file = f;
		socket = s;

		try {
			out = new DataOutputStream(socket.getOutputStream());
			in = new DataInputStream(socket.getInputStream());
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
				 * The Server sends the msg to the Client.
				 */
				System.err.println("Sending: " + msg);
				out.writeUTF(msg);

				/*
				 * The Server receives the instruction here.
				 */
				response = in.readUTF();

				if (response.equals("N")) {
					/*
					 * Server upgrades the CopyBlock to a NewBlock instruction
					 * and sends it.
					 */
					Instruction upgraded = new NewBlockInstruction(
							(CopyBlockInstruction) inst);
					String msg2 = upgraded.ToJSON();

					System.err.println("Sending: " + msg2);
					out.writeUTF(msg2);
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
				Server applicationServer = new Server();
				applicationServer.isThreadAlive = false;
				//Server.isThreadAlive = false;
			} catch (IOException e) {
				e.printStackTrace();
			}

		} // get next instruction loop forever
	}
}

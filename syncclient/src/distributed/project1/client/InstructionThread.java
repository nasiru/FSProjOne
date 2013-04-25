package distributed.project1.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class InstructionThread implements Runnable {

	SynchronisedFile file;
	Socket clientSocket;

	DataOutputStream outToServer;
	DataInputStream inFromServer;

	InstructionThread(SynchronisedFile f, Socket s) {
		file = f;
		clientSocket = s;

		try {
			outToServer = new DataOutputStream(clientSocket.getOutputStream());
			inFromServer = new DataInputStream(clientSocket.getInputStream());
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
				 * Pretend the Client sends the msg to the Server.
				 */
				System.err.println("Sending: " + msg);
				outToServer.writeUTF(msg);

				// network delay

				/*
				 * The Server receives the instruction here.
				 */
				response = inFromServer.readUTF();

				if (response.equals("N")) {
					/*
					 * Client upgrades the CopyBlock to a NewBlock instruction
					 * and sends it.
					 */
					Instruction upgraded = new NewBlockInstruction(
							(CopyBlockInstruction) inst);
					String msg2 = upgraded.ToJSON();

					System.err.println("Sending: " + msg2);
					outToServer.writeUTF(msg2);
				} else if (response.equals("Y")) { // success

					/*
					 * If using a synchronous RequestReply protocol, the server
					 * can now acknowledge that the block was correctly
					 * received, and the next instruction can be sent.
					 */

					// network delay

					/*
					 * Client receives acknowledgement and moves on to process
					 * next instruction.
					 */

				}

			} catch (IOException e) {

				e.printStackTrace();
			}

		} // get next instruction loop forever
	}
}

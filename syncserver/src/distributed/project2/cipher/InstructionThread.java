package distributed.project2.cipher;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
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

	String protocol = null;
	String blockSize = null;

	InstructionThread(SynchronisedFile f, Socket s, String p) {
		file = f;
		socket = s;
		protocol = p;

		try {
			out = new DataOutputStream(socket.getOutputStream());
			in = new DataInputStream(socket.getInputStream());
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public void run() {

		Thread fut = new Thread(new FileUpdateThread(file));
		fut.start();

		while (true) {

			if (protocol.equals("S")) {
				if (!receive(file, out, in)) {
					break;
				}
			} else if (protocol.equals("R")) {
				if (!send(file, out, in)) {
					break;
				}
			}

		}

	}

	private boolean receive(SynchronisedFile file, DataOutputStream out,
			DataInputStream in) {

		Instruction receivedInst = null;

		//
		// inner infinite loop for synchronization proper
		//
		InstructionFactory instFact = new InstructionFactory();

		while (true) {

			try {
				// wait for instruction
				receivedInst = instFact.FromJSON(in.readUTF());

				// The Server processes the instruction
				file.ProcessInstruction(receivedInst);
				out.writeUTF("Y");
			} catch (SocketException e) {
				System.out.println("Client closed connection");
				return false;
			} catch (EOFException e) {
				// catch if client disconnected and go back to listening
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

					out.writeUTF("N");

					// network delay

					/*
					 * Server receives the NewBlock instruction.
					 */
					receivedInst = instFact.FromJSON(in.readUTF());

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
		return true;
	}

	private boolean send(SynchronisedFile file, DataOutputStream out,
			DataInputStream in) {
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
				return false;
			} catch (IOException e) {
				e.printStackTrace();
			}

		} // get next instruction loop forever

		return true;
	}
}

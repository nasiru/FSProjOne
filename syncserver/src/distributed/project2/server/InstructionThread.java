package distributed.project2.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

import org.bouncycastle.util.encoders.Base64;

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

	ObjectOutputStream out;
	ObjectInputStream in;

	String protocol = null;
	String blockSize = null;

	SecretKey symmetricKey;

	InstructionThread(SynchronisedFile f, Socket s, String p, SecretKey sk) {
		file = f;
		socket = s;
		protocol = p;
		symmetricKey = sk;

		try {
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());
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

	private boolean receive(SynchronisedFile file, ObjectOutputStream out,
			ObjectInputStream in) {

		Instruction receivedInst = null;

		//
		// inner infinite loop for synchronization proper
		//
		InstructionFactory instFact = new InstructionFactory();

		while (true) {

			try {
				// wait for instruction
				receivedInst = instFact.FromJSON(new String(HybridCipher
						.decrypt((byte[]) in.readObject(), symmetricKey)));

				// The Server processes the instruction
				file.ProcessInstruction(receivedInst);
				out.writeObject(HybridCipher.encrypt("Y".getBytes(),
						symmetricKey));

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

					out.writeObject(HybridCipher.encrypt("N".getBytes(),
							symmetricKey));

					// network delay

					/*
					 * Server receives the NewBlock instruction.
					 */
					receivedInst = instFact.FromJSON(new String(HybridCipher
							.decrypt((byte[]) in.readObject(), symmetricKey)));

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
				} catch (ClassNotFoundException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return true;
	}

	private boolean send(SynchronisedFile file, ObjectOutputStream out,
			ObjectInputStream in) {
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
				out.writeUTF(new String(Base64.encode(HybridCipher.encrypt(
						msg.getBytes(), symmetricKey))));

				/*
				 * The Server receives the instruction here.
				 */
				response = HybridCipher.decrypt(in.readUTF().getBytes(),
						symmetricKey).toString();

				if (response.equals("N")) {
					/*
					 * Server upgrades the CopyBlock to a NewBlock instruction
					 * and sends it.
					 */
					Instruction upgraded = new NewBlockInstruction(
							(CopyBlockInstruction) inst);
					String msg2 = upgraded.ToJSON();

					System.err.println("Sending: " + msg2);
					out.writeUTF(new String(Base64.encode(HybridCipher.encrypt(
							msg2.getBytes(), symmetricKey))));
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
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			}

		} // get next instruction loop forever

		return true;
	}
}

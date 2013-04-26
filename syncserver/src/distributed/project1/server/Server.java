package distributed.project1.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

	static ServerSocket serverSocket = null;
	static Socket connectionSocket = null;

	static SynchronisedFile file = null;

	static DataInputStream inFromClient = null;
	static DataOutputStream outToClient = null;

	static String protocol = null;
	static String blockSize = null;

	static Instruction receivedInst = null;

	public static void main(String[] args) {

		try {
			serverSocket = new ServerSocket(7654);

			System.out.println("Listening to port: "
					+ serverSocket.getLocalPort());

			while (true) {
				connectionSocket = serverSocket.accept();

				inFromClient = new DataInputStream(
						connectionSocket.getInputStream());

				outToClient = new DataOutputStream(
						connectionSocket.getOutputStream());

				System.out.println("Connected to client");
				outToClient.writeUTF("(S) Sending or (R) Receiving?");
				protocol = inFromClient.readUTF();

				outToClient.writeUTF("Enter block size: ");
				blockSize = inFromClient.readUTF();

				System.out.println("Received: " + protocol + " and "
						+ blockSize);

				outToClient.writeUTF("Got it. Starting synchronization...");

				// Open file with blocksize
				file = new SynchronisedFile(args[0],
						Integer.parseInt(blockSize));

				if (protocol.equals("S")) {
					receive();
				} else if (protocol.equals("R")) {
					send();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void send() {

		Thread stt = new Thread(new InstructionThread(file, connectionSocket));
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

		while (!serverSocket.isClosed()) {

			try {
				// wait for instruction
				receivedInst = instFact.FromJSON(inFromClient.readUTF());

				// The Server processes the instruction
				file.ProcessInstruction(receivedInst);
				outToClient.writeUTF("Y");
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

					outToClient.writeUTF("N");

					// network delay

					/*
					 * Server receives the NewBlock instruction.
					 */
					receivedInst = instFact.FromJSON(inFromClient.readUTF());

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
	}
}

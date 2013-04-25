package distributed.project1.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

	public static void main(String[] args) {

		ServerSocket serverSocket = null;

		SynchronisedFile file = null;

		DataInputStream inFromClient = null;
		DataOutputStream outToClient = null;

		String protocol = null;
		String blockSize = null;

		//
		// variables for inner loop
		//

		try {
			serverSocket = new ServerSocket(7654);

			System.out.println("Listening to port: "
					+ serverSocket.getLocalPort());

			while (true) {
				Socket connectionSocket = serverSocket.accept();

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

				//
				// inner infinite loop for synchronization proper
				//
				InstructionFactory instFact = new InstructionFactory();

				while (!connectionSocket.isClosed()) {

					// wait for instruction
					Instruction receivedInst = instFact.FromJSON(inFromClient
							.readUTF());

					try {
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
							 * At this point the Server needs to send a request
							 * back to the Client to obtain the actual bytes of
							 * the block.
							 */

							outToClient.writeUTF("N");

							// network delay

							/*
							 * Server receives the NewBlock instruction.
							 */
							receivedInst = instFact.FromJSON(inFromClient
									.readUTF());

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

		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

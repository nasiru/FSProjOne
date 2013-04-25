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
				while (!connectionSocket.isClosed()) {

				}

			}

		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}

package distributed.project1.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Client {

	public static void main(String[] args) {

		Socket clientSocket = null;

		SynchronisedFile file = null;

		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(
				System.in));

		String protocol = null;
		String blockSize = null;

		try {

			// if (args.length == 2) {
			clientSocket = new Socket(args[0], 7654);
			// } else {
			// clientSocket = new Socket(args[0], Integer.parseInt(args[2]));
			// }

			DataOutputStream outToServer = new DataOutputStream(
					clientSocket.getOutputStream());

			DataInputStream inFromServer = new DataInputStream(
					clientSocket.getInputStream());

			// outToServer.writeUTF(args[1]);

			// S or R
			System.out.println(inFromServer.readUTF());
			protocol = inFromUser.readLine();
			outToServer.writeUTF(protocol);

			// Blocksize
			System.out.println(inFromServer.readUTF());
			blockSize = inFromUser.readLine();
			outToServer.writeUTF(blockSize);

			// Start sync
			System.out.println(inFromServer.readUTF());

			// Open file with blocksize
			file = new SynchronisedFile(args[1], Integer.parseInt(blockSize));

		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("HERE");
		Thread stt = new Thread(new InstructionThread(file, clientSocket));
		stt.start();
		System.out.println("HERE2");
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
}

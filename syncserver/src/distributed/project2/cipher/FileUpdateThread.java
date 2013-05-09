package distributed.project2.cipher;

import java.io.IOException;

public class FileUpdateThread implements Runnable {

	SynchronisedFile file = null;

	FileUpdateThread(SynchronisedFile f) {
		file = f;
	}

	public void run() {
		while (true) {
			// skip if the file is not modified
			System.err.println("SynchTest: calling fromFile.CheckFileState()");
			try {
				file.CheckFileState();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}

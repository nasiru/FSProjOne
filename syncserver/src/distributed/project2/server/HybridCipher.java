package distributed.project2.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/* HybridCipher.java
 * 
 * Authors: Erick Gaspar and Nasir Uddin
 * 
 * Description: Contains all cipher related methods for this program. This differs from
 * the Client version because it generates an asymmetric keypair instead of a symmetric key.
 * 
 * The algorithms listed here must coincide with the client's algorithms in order
 * for encryption/decryption to work.
 * 
 */

public class HybridCipher {

	// asymmetric algorithms used
	public static String asymKeyAlgorithm = "RSA";
	public static String asymAlgorithm = "RSA/ECB/OAEPWithMD5AndMGF1Padding";
	public static int asymKeyAlgorithmStrength = 1024;
	public static String signatureAlgorithm = "SHA1WithRSAEncryption";

	// symmetric algorithms used
	public static String symKeyAlgorithm = "RIJNDAEL";
	public static String symAlgorithm = "RIJNDAEL";
	public static int symAlgorithmStrength = 128;

	private static File privfile = new File("priv.kp");
	private static File pubfile = new File("pub.kp");

	public static KeyPair init() {

		try {
			// make sure the BC provider is registered.
			Security.addProvider(new BouncyCastleProvider());

			// check if keypair was already generated and stored
			if (privfile.exists() && pubfile.exists()) {

				System.out.println("Key files found, loading keys . . .");

				// read private key from file
				FileInputStream privin = new FileInputStream(privfile);

				byte[] privbytes = new byte[(int) privfile.length()];

				privin.read(privbytes);
				privin.close();

				// read public key from file
				FileInputStream pubin = new FileInputStream(pubfile);

				byte[] pubbytes = new byte[(int) pubfile.length()];

				pubin.read(pubbytes);
				pubin.close();

				// load both
				KeyFactory keyFactory = KeyFactory.getInstance(
						asymKeyAlgorithm, "BC");

				X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(
						pubbytes);
				PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

				PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(
						privbytes);
				PrivateKey privateKey = keyFactory
						.generatePrivate(privateKeySpec);

				return new KeyPair(publicKey, privateKey);

			} else {

				SecureRandom sr = new SecureRandom();

				// Generate keys
				KeyPairGenerator gen = KeyPairGenerator.getInstance(
						asymKeyAlgorithm, "BC");
				gen.initialize(asymKeyAlgorithmStrength, sr);

				System.out.println("Generating key . . .");

				KeyPair kp = gen.generateKeyPair();

				PrivateKey privateKey = kp.getPrivate();
				PublicKey publicKey = kp.getPublic();

				// save private key
				privfile.createNewFile();
				FileOutputStream privout = new FileOutputStream(privfile);
				PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(
						privateKey.getEncoded());
				privout.write(pkcs8EncodedKeySpec.getEncoded());
				privout.close();

				// save public key
				pubfile.createNewFile();
				FileOutputStream pubout = new FileOutputStream(pubfile);
				X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(
						publicKey.getEncoded());
				pubout.write(x509EncodedKeySpec.getEncoded());
				pubout.close();

				return kp;
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	// decrypt the received password using your private key
	public static boolean verifyPassword(byte[] password, SecretKey sk,
			String serverPass) {

		// decrypt the message using the secret key
		String decryptedPassword = null;

		try {
			decryptedPassword = new String(decrypt(password, sk));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

		System.out.println("Decrypted password: [" + decryptedPassword + "] ");

		return decryptedPassword.equals(serverPass);
	}

	// decrypt the symmetric key sent by the client using your private key
	public static SecretKey decryptKey(byte[] encryptedSecretKey, KeyPair kp) {

		byte[] encodedSecretKey = null;

		try {
			encodedSecretKey = decrypt(encryptedSecretKey, kp.getPrivate());
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

		SecretKey sKey = new SecretKeySpec(encodedSecretKey, symAlgorithm);
		System.out.println("Secret key decoded.");

		return sKey;

	}

	// encrypt the message using the provided symmetric key
	public static byte[] encrypt(byte[] toEncrypt, SecretKey key)
			throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance(symAlgorithm);
		cipher.init(Cipher.ENCRYPT_MODE, key);

		byte[] result = cipher.doFinal(toEncrypt);
		return result;
	}

	// encrypt using the generated public key (not used but kept for posterity)
	public static byte[] encrypt(byte[] toEncrypt, PublicKey key)
			throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance(asymAlgorithm);
		cipher.init(Cipher.ENCRYPT_MODE, key);

		byte[] result = cipher.doFinal(toEncrypt);
		return result;
	}

	// decrypts the message using the symmetric key provided
	public static byte[] decrypt(byte[] toDecrypt, SecretKey key)
			throws GeneralSecurityException {

		Cipher deCipher = Cipher.getInstance(symAlgorithm);
		deCipher.init(Cipher.DECRYPT_MODE, key);

		byte[] result = deCipher.doFinal(toDecrypt);
		return result;
	}

	// decrypts the message using the generated private key
	public static byte[] decrypt(byte[] toDecrypt, PrivateKey key)
			throws GeneralSecurityException {

		Cipher deCipher = Cipher.getInstance(asymAlgorithm);
		deCipher.init(Cipher.DECRYPT_MODE, key);

		byte[] result = deCipher.doFinal(toDecrypt);
		return result;
	}
}
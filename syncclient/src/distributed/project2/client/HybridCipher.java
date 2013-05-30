package distributed.project2.client;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;

/* HybridCipher.java
 * 
 * Authors: Erick Gaspar and Nasir Uddin
 * 
 * Description: Contains all cipher related methods for this program. This differs from
 * the Server version because it generates a symmetric key instead of an asymmetric keypair.
 * 
 * The algorithms listed here must coincide with the server's algorithms in order
 * for encryption/decryption to work.
 * 
 */

public class HybridCipher {

	// asymmetric algorithms used
	public static String asymKeyAlgorithm = "RSA";
	public static String asymAlgorithm = "RSA/ECB/OAEPWithMD5AndMGF1Padding";
	public static int asymKeyAlgorithmStrength = 1024;

	// symmetric algorithms used
	public static String symKeyAlgorithm = "RIJNDAEL";
	public static String symAlgorithm = "RIJNDAEL";
	public static int symAlgorithmStrength = 256;

	private static SecretKey cipherKey = null;

	public static byte[] init(PublicKey pk, String password) {

		try {
			// make sure the BC provider is registered.
			Security.addProvider(new BouncyCastleProvider());

			SecureRandom sr = new SecureRandom();

			// generate a random secret key
			KeyGenerator kg = KeyGenerator.getInstance(symKeyAlgorithm);
			kg.init(symAlgorithmStrength, sr);
			cipherKey = kg.generateKey();
			System.out.println("Generated cipher key, proceeding: "
					+ cipherKey.getAlgorithm());

			// encrypt the password using the secret key
			byte[] encryptedData = encrypt(password.getBytes());

			System.out.println("Encrypted byte count: " + encryptedData.length);
			System.out.println("Encrypted password: ["
					+ new String(Base64.encode(encryptedData)) + "]");

			return encryptedData;

		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return null;

	}

	// encrypt the secret key using the server's public key
	public static byte[] getEncryptedKey(PublicKey pk) {

		byte[] encryptedSecretKey = null;

		try {
			encryptedSecretKey = encrypt(cipherKey.getEncoded(), pk);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

		return encryptedSecretKey;
	}

	// encrypt the message using the symmetric key
	public static byte[] encrypt(byte[] toEncrypt)
			throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance(symAlgorithm);
		//System.out.println("got cipher, blocksize = " + cipher.getBlockSize());
		cipher.init(Cipher.ENCRYPT_MODE, cipherKey);

		byte[] result = cipher.doFinal(toEncrypt);
		return result;
	}

	// encrypts using the provided server public key
	public static byte[] encrypt(byte[] toEncrypt, PublicKey key)
			throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance(asymAlgorithm);
		cipher.init(Cipher.ENCRYPT_MODE, key);

		byte[] result = cipher.doFinal(toEncrypt);
		return result;
	}

	// decrypts using the symmetric key
	public static byte[] decrypt(byte[] toDecrypt)
			throws GeneralSecurityException {

		Cipher deCipher = Cipher.getInstance(symAlgorithm);
		deCipher.init(Cipher.DECRYPT_MODE, cipherKey);

		byte[] result = deCipher.doFinal(toDecrypt);
		return result;
	}

	// decrypts using the private key (not used by the client, but kept here for posterity)
	public static byte[] decrypt(byte[] toDecrypt, PrivateKey key)
			throws GeneralSecurityException {

		Cipher deCipher = Cipher.getInstance(asymAlgorithm);
		deCipher.init(Cipher.DECRYPT_MODE, key);

		byte[] result = deCipher.doFinal(toDecrypt);
		return result;
	}
}
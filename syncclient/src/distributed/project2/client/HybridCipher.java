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

			/***
			 * on the producer side: 1. Generate a secret key. 2. Use asymmetric
			 * algorithm to encrypt the secret key for consumer 3. Use symmetric
			 * algorithm to encrypt message using the secret key
			 ***/
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

	public static byte[] getEncryptedKey(PublicKey pk) {
		// encrypt the secret key using the consumer's public key
		byte[] encryptedSecretKey = null;

		try {
			encryptedSecretKey = encrypt(cipherKey.getEncoded(), pk);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

		return encryptedSecretKey;
	}

	public static byte[] encrypt(byte[] toEncrypt)
			throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance(symAlgorithm);
		//System.out.println("got cipher, blocksize = " + cipher.getBlockSize());
		cipher.init(Cipher.ENCRYPT_MODE, cipherKey);

		byte[] result = cipher.doFinal(toEncrypt);
		return result;
	}

	public static byte[] encrypt(byte[] toEncrypt, PublicKey key)
			throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance(asymAlgorithm);
		cipher.init(Cipher.ENCRYPT_MODE, key);

		byte[] result = cipher.doFinal(toEncrypt);
		return result;
	}

	public static byte[] decrypt(byte[] toDecrypt)
			throws GeneralSecurityException {

		Cipher deCipher = Cipher.getInstance(symAlgorithm);
		deCipher.init(Cipher.DECRYPT_MODE, cipherKey);

		byte[] result = deCipher.doFinal(toDecrypt);
		return result;
	}

	public static byte[] decrypt(byte[] toDecrypt, PrivateKey key)
			throws GeneralSecurityException {

		Cipher deCipher = Cipher.getInstance(asymAlgorithm);
		deCipher.init(Cipher.DECRYPT_MODE, key);

		byte[] result = deCipher.doFinal(toDecrypt);
		return result;
	}
}
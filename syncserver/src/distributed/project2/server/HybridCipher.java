package distributed.project2.server;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class HybridCipher {

	// asymmetric algorithms used
	public static String asymKeyAlgorithm = "RSA";
	public static String asymAlgorithm = "RSA/ECB/OAEPWithMD5AndMGF1Padding";
	public static int asymKeyAlgorithmStrength = 1024;
	public static String signatureAlgorithm = "SHA1WithRSAEncryption";

	// symmetric algorithms used
	public static String symKeyAlgorithm = "RIJNDAEL";
	public static String symAlgorithm = "RIJNDAEL";
	public static int symAlgorithmStrength = 256;

	public static KeyPair init() {

		try {
			// make sure the BC provider is registered.
			Security.addProvider(new BouncyCastleProvider());

			SecureRandom sr = new SecureRandom();

			/***
			 * Generate consumer keys for test purposes. In Real Life(TM) the
			 * producer would need to know only the consumer's public key.
			 ***/
			KeyPairGenerator gen = KeyPairGenerator.getInstance(
					asymKeyAlgorithm, "BC");
			gen.initialize(asymKeyAlgorithmStrength, sr);

			System.out.println("Generating key . . .");
			return gen.generateKeyPair();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/***
	 * and now on the consumer side: 1. Use asymmetric algorithm and consumer's
	 * private key to decrypt the secret key 2. Use symmetric algorithm and
	 * secret key to decrypt message.
	 ***/
	public static boolean verifyPassword(byte[] password, SecretKey sk,
			String serverPass) {

		// decrypt the message using the secret key
		String decryptedPassword = null;

		try {
			decryptedPassword = new String(decrypt(password, sk));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		}

		System.out.println("Password decoded, byte count: "
				+ decryptedPassword.length());
		System.out.println("Decrypted password: [" + decryptedPassword + "] ");

		return decryptedPassword.equals(serverPass);
	}

	public static SecretKey decryptKey(byte[] encryptedSecretKey, KeyPair kp) {
		// first get the secret key back with the consumer's private key
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

	public static byte[] encrypt(byte[] toEncrypt, SecretKey key)
			throws GeneralSecurityException {

		Cipher cipher = Cipher.getInstance(symAlgorithm);
		System.out.println("got cipher, blocksize = " + cipher.getBlockSize());
		cipher.init(Cipher.ENCRYPT_MODE, key);

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

	public static byte[] decrypt(byte[] toDecrypt, SecretKey key)
			throws GeneralSecurityException {

		Cipher deCipher = Cipher.getInstance(symAlgorithm);
		deCipher.init(Cipher.DECRYPT_MODE, key);

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
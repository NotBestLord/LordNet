package com.notlord.lordnet.secured;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class SecuredUtilities {
	protected static byte[] encryptPacketMessage(PublicKey publicKey, byte[] msg) throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		Cipher encrypt = Cipher.getInstance("RSA");
		encrypt.init(Cipher.ENCRYPT_MODE, publicKey);
		return encrypt.doFinal(msg);
	}
	protected static String decryptPacketMessage(PrivateKey privateKey, byte[] msg) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Cipher decrypt = Cipher.getInstance("RSA");
		decrypt.init(Cipher.DECRYPT_MODE, privateKey);
		return new String(decrypt.doFinal(msg), StandardCharsets.UTF_8);
	}
}

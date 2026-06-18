package cn.scarefree.openlink2.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Arrays;

public class AesUtils {
	private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
	private static final int GCM_IV_LENGTH = 12; // 96 bits
	private static final int GCM_TAG_LENGTH = 128; // bits

	/**
	 * 基于机器特征生成秘钥
	 */
	public static SecretKey deriveKeyFromMachine() {
		try {
			String seed = System.getProperty("user.name") + System.getProperty("os.arch");
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = Arrays.copyOf(sha.digest(seed.getBytes(StandardCharsets.UTF_8)), 16);
			return new SecretKeySpec(keyBytes, "AES");
		} catch (Exception e) {
			throw new RuntimeException("Failed to derive key", e);
		}
	}

	/**
	 * AES-GCM 加密
	 * @return IV + 密文 的组合字节数组
	 */
	public static byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
		Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
		byte[] iv = new byte[GCM_IV_LENGTH];
		SecureRandom random = new SecureRandom();
		random.nextBytes(iv);
		GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
		cipher.init(Cipher.ENCRYPT_MODE, key, spec);
		byte[] ciphertext = cipher.doFinal(plaintext);
		// 将 IV 前置到密文
		byte[] combined = new byte[iv.length + ciphertext.length];
		System.arraycopy(iv, 0, combined, 0, iv.length);
		System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
		return combined;
	}

	/**
	 * AES-GCM 解密
	 * @param combined IV + 密文
	 */
	public static byte[] decrypt(byte[] combined, SecretKey key) throws Exception {
		if (combined.length < GCM_IV_LENGTH + 16) {
			throw new IllegalArgumentException("Invalid encrypted data");
		}
		byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
		byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
		Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
		GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
		cipher.init(Cipher.DECRYPT_MODE, key, spec);
		return cipher.doFinal(ciphertext);
	}
}

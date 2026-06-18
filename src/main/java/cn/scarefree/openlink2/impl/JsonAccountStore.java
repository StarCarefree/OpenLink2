package cn.scarefree.openlink2.impl;

import cn.scarefree.openlink2.OpenLink2;
import cn.scarefree.openlink2.api.account.Account;
import cn.scarefree.openlink2.api.account.AccountStore;
import cn.scarefree.openlink2.utils.AesUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

public class JsonAccountStore implements AccountStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	@Getter
	private final Path accountsDir;
	private final SecretKey aesKey;

	/**
	 * 创建使用默认目录的存储实例。
	 */
	public JsonAccountStore() {
		this(getAccountsPath(), OpenLink2.AES_KEY);
	}

	/**
	 * 创建使用指定目录的存储实例。
	 */
	public JsonAccountStore(Path accountsDir, SecretKey aesKey) {
		this.accountsDir = accountsDir;
		this.aesKey = aesKey;
	}

	/**
	 * 获取accounts目录
	 */
	public static Path getAccountsPath() {
		return OpenLink2.STORAGE_PATH.resolve("accounts");
	}

	// 文件名安全处理
	private String sanitize(String input) {
		return input.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private Path getAccountFile(String platformId, String platformUserId) {
		String name = sanitize(platformId) + "_" + sanitize(platformUserId) + ".account";
		return getAccountsDir().resolve(name);
	}

	@Override
	public void save(Account account) throws IOException {
		lock.writeLock().lock();
		try {
			Files.createDirectories(getAccountsDir());
			Path file = getAccountFile(account.getPlatformId(), account.getPlatformUserId());
			AccountImpl impl = (account instanceof AccountImpl) ? (AccountImpl) account : AccountImpl.from(account);
			String json = GSON.toJson(impl);
			byte[] encrypted = AesUtils.encrypt(json.getBytes(StandardCharsets.UTF_8), aesKey);
			Files.write(file, encrypted, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (Exception e) {
			throw new IOException("Encryption failed", e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private String readDecrypted(Path file) throws IOException {
		try {
			byte[] encrypted = Files.readAllBytes(file);
			byte[] decrypted = AesUtils.decrypt(encrypted, aesKey);
			return new String(decrypted, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IOException("Decryption failed", e);
		}
	}

	@Override
	public Optional<Account> get(String platformId, String platformUserId) {
		lock.readLock().lock();
		try {
			Path file = getAccountFile(platformId, platformUserId);
			if (Files.exists(file)) {
				String json = readDecrypted(file);
				AccountImpl impl = GSON.fromJson(json, AccountImpl.class);
				return Optional.of(impl);
			}
			return Optional.empty();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read account", e);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public List<Account> listByPlatform(String platformId) {
		List<Account> all = listAll();
		List<Account> result = new ArrayList<>();
		for (Account a : all) {
			if (a.getPlatformId().equals(platformId)) {
				result.add(a);
			}
		}
		return result;
	}

	@Override
	public List<Account> listAll() {
		lock.readLock().lock();
		try {
			Path dir = getAccountsDir();
			if (!Files.exists(dir)) {
				return Collections.emptyList();
			}
			List<Account> list = new ArrayList<>();
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(p -> p.toString().endsWith(".account"))
						.forEach(file -> {
							try {
								String json = readDecrypted(file);
								AccountImpl impl = GSON.fromJson(json, AccountImpl.class);
								list.add(impl);
							} catch (IOException e) {
								OpenLink2.LOGGER.error("Failed to read the account file", e);
							}
						});
			}
			return list;
		} catch (IOException e) {
			throw new RuntimeException("Failed to list accounts", e);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void delete(String platformId, String platformUserId) {
		lock.writeLock().lock();
		try {
			Path file = getAccountFile(platformId, platformUserId);
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new RuntimeException("Failed to delete account", e);
		} finally {
			lock.writeLock().unlock();
		}
	}
}

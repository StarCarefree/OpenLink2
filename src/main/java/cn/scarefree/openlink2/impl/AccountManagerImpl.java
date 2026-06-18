package cn.scarefree.openlink2.impl;

import cn.scarefree.openlink2.api.account.*;
import cn.scarefree.openlink2.api.exceptions.PlatformNotSupportedException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AccountManagerImpl implements AccountManager {

	private final Map<String, AccountPlatform> platforms = new ConcurrentHashMap<>();
	private final AccountStore accountStore;

	public AccountManagerImpl() {
		this(new JsonAccountStore());
	}

	public AccountManagerImpl(AccountStore accountStore) {
		this.accountStore = Objects.requireNonNull(accountStore, "accountStore must not be null");
	}

	@Override
	public void registerPlatform(AccountPlatform platform) {
		platforms.put(platform.getPlatformId(), platform);
	}

	@Override
	public CompletableFuture<LoginRequestInfo> login(String platformId) {
		AccountPlatform platform = getPlatformOrThrow(platformId);
		return platform.startLogin();
	}

	@Override
	public CompletableFuture<Account> completeLogin(String platformId, LoginRequestInfo requestInfo) {
		AccountPlatform platform = 	getPlatformOrThrow(platformId);
		return platform.completeLogin(requestInfo)
				.thenApply(account -> {
					try {
						accountStore.save(account);
					} catch (Exception e) {
						throw new RuntimeException("Failed to save account after login", e);
					}
					return account;
				});
	}

	@Override
	public List<Account> getAccounts(String platformId) {
		try {
			return accountStore.listByPlatform(platformId);
		} catch (Exception e) {
			throw new RuntimeException("Failed to retrieve accounts for platform: " + platformId, e);
		}
	}

	@Override
	public List<Account> getAllAccounts() {
		try {
			return accountStore.listAll();
		} catch (Exception e) {
			throw new RuntimeException("Failed to retrieve all accounts", e);
		}
	}

	@Override
	public void removeAccount(String platformId, String platformUserId) {
		try {
			accountStore.delete(platformId, platformUserId);
		} catch (Exception e) {
			throw new RuntimeException("Failed to remove account: " + platformId + "/" + platformUserId, e);
		}
	}

	@Override
	public CompletableFuture<Account> ensureFreshAccount(String platformId, String platformUserId) {
		AccountPlatform platform = getPlatformOrThrow(platformId);
		try {
			Account account = accountStore.get(platformId, platformUserId)
					.orElseThrow(() -> new IllegalArgumentException("Account not found: " + platformId + "/" + platformUserId));
			if (platform.isTokenValid(account)) {
				return CompletableFuture.completedFuture(account);
			}
			return platform.refresh(account)
					.thenApply(refreshed -> {
						try {
							accountStore.save(refreshed);
						} catch (Exception e) {
							throw new RuntimeException("Failed to save refreshed account", e);
						}
						return refreshed;
					});
		} catch (Exception e) {
			CompletableFuture<Account> future = new CompletableFuture<>();
			future.completeExceptionally(e);
			return future;
		}
	}

	private AccountPlatform getPlatformOrThrow(String platformId) {
		AccountPlatform platform = platforms.get(platformId);
		if (platform == null) {
			throw new PlatformNotSupportedException(platformId);
		}
		return platform;
	}
}

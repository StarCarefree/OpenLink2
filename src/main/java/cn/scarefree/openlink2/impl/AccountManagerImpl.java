package cn.scarefree.openlink2.impl;

import cn.scarefree.openlink2.api.account.Account;
import cn.scarefree.openlink2.api.account.AccountManager;
import cn.scarefree.openlink2.api.account.AccountPlatform;
import cn.scarefree.openlink2.api.account.LoginRequestInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

//TODO
public class AccountManagerImpl implements AccountManager {
	@Override
	public void registerPlatform(AccountPlatform platform) {

	}

	@Override
	public CompletableFuture<LoginRequestInfo> login(String platformId) {
		return null;
	}

	@Override
	public CompletableFuture<Account> completeLogin(String platformId, LoginRequestInfo requestInfo) {
		return null;
	}

	@Override
	public List<Account> getAccounts(String platformId) {
		return List.of();
	}

	@Override
	public List<Account> getAllAccounts() {
		return List.of();
	}

	@Override
	public void removeAccount(String platformId, String platformUserId) {

	}

	@Override
	public CompletableFuture<Account> ensureFreshAccount(String platformId, String platformUserId) {
		return null;
	}
}

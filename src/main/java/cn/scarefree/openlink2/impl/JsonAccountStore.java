package cn.scarefree.openlink2.impl;

import cn.scarefree.openlink2.api.account.Account;
import cn.scarefree.openlink2.api.account.AccountStore;

import java.util.List;
import java.util.Optional;

//TODO AES加密
public class JsonAccountStore implements AccountStore {
	@Override
	public void save(Account account) throws Exception {

	}

	@Override
	public List<Account> listByPlatform(String platformId) {
		return List.of();
	}

	@Override
	public Optional<Account> get(String platformId, String platformUserId) {
		return Optional.empty();
	}

	@Override
	public void delete(String platformId, String platformUserId) {

	}

	@Override
	public List<Account> listAll() {
		return List.of();
	}
}

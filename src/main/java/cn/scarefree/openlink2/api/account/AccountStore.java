package cn.scarefree.openlink2.api.account;

import java.util.List;
import java.util.Optional;

public interface AccountStore {
	/**
	 * 保存或更新一个账号
	 */
	void save(Account account) throws Exception;

	/**
	 * 列出指定平台下所有已绑定账号
	 */
	List<Account> listByPlatform(String platformId) throws Exception;

	/**
	 * 根据平台和平台用户 ID 获取单个账号
	 */
	Optional<Account> get(String platformId, String platformUserId);

	/**
	 * 删除指定账号
	 */
	void delete(String platformId, String platformUserId) throws Exception;

	/**
	 * 获取本地所有已保存账号
	 */
	List<Account> listAll() throws Exception;
}

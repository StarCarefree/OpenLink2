package cn.scarefree.openlink2.api.account;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AccountManager {
	/**
	 * 注册一个平台提供者（Mod 初始化时调用）
	 */
	void registerPlatform(AccountPlatform platform);

	/**
	 * 发起登录
	 */
	CompletableFuture<LoginRequestInfo> login(String platformId);

	/**
	 * 完成登录并自动保存到本地存储
	 */
	CompletableFuture<Account> completeLogin(String platformId, LoginRequestInfo requestInfo);

	/**
	 * 获取指定平台所有已绑定账号
	 */
	List<Account> getAccounts(String platformId);

	/**
	 * 获取所有已绑定账号
	 */
	List<Account> getAllAccounts();

	/**
	 * 解绑并删除指定账号
	 */
	void removeAccount(String platformId, String platformUserId);

	/**
	 * 保证账号令牌有效，必要时自动刷新
	 * @return 直接可使用的 {@link Account}
	 */
	CompletableFuture<Account> ensureFreshAccount(String platformId, String platformUserId);
}

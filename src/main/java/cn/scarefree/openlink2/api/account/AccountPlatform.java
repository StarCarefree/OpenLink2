package cn.scarefree.openlink2.api.account;

import java.util.concurrent.CompletableFuture;

public interface AccountPlatform {
	/**
	 * @return 平台唯一标识，对应 {@link Account#getPlatformId()}
	 */
	String getPlatformId();

	/**
	 * 启动登录流程
	 * @return 下一步需要给用户展示的信息
	 */
	CompletableFuture<LoginRequestInfo> startLogin();

	/**
	 * 完成登录流程
	 * @return 绑定好的 {@link Account}
	 */
	CompletableFuture<Account> completeLogin(LoginRequestInfo requestInfo);

	/**
	 * 刷新用户访问令牌
	 * @return 更新后的 {@link Account}
	 */
	CompletableFuture<Account> refresh(Account account);

	/**
	 * 判断令牌是否仍然有效
	 * @return 令牌是否有效
	 */
	default boolean isTokenValid(Account account) {
		return !account.isExpired();
	}
}

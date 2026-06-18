package cn.scarefree.openlink2.api.account;

import org.jetbrains.annotations.Nullable;

public interface Account {
	/**
	 * @return 平台唯一标识，对应 {@link AccountPlatform#getPlatformId()}
	 */
	String getPlatformId();

	/**
	 * @return 平台侧用户唯一 ID
	 */
	String getPlatformUserId();

	/**
	 * @return 显示名称
	 */
	String getDisplayName();

	/**
	 * @return 用户Email，若没有返回 {@code null}
	 */
	@Nullable
	String getEmail();

	/**
	 * @return 用户头像，若没有返回 {@code null}
	 */
	@Nullable
	String getAvatarUrl();

	/**
	 * @return 判断凭据是否已过期
	 */
	boolean isExpired();

	/**
	 * 获取访问令牌，仅限内部使用，不对外暴露修改能力。
	 * 外部调用者应通过 AccountManager.ensureFreshAccount() 获取有效令牌。
	 * @return 访问令牌
	 */
	String getAccessToken();

	/**
	 * 获取刷新令牌（内部使用）。
	 * @return 刷新令牌
	 */
	String getRefreshToken();
}

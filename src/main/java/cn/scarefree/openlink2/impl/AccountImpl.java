package cn.scarefree.openlink2.impl;

import cn.scarefree.openlink2.api.account.Account;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AccountImpl implements Account {
	private String platformId;
	private String platformUserId;
	private String displayName;
	private String email;
	private String avatarUrl;
	private String accessToken;
	private String refreshToken;
	private long expiresAt;
	public static AccountImpl from(Account account) {
		return new AccountImpl(
				account.getPlatformId(),
				account.getPlatformUserId(),
				account.getDisplayName(),
				account.getEmail(),
				account.getAvatarUrl(),
				account.getAccessToken(),
				account.getRefreshToken(),
				account.getExpiresAt()
		);
	}
}

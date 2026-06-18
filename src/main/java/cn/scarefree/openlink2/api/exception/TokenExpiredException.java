package cn.scarefree.openlink2.api.exception;

public class TokenExpiredException extends AuthException {
	public TokenExpiredException(String platformId, String userId) {
		super("Token expired for " + platformId + "/" + userId);
	}
}

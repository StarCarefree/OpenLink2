package cn.scarefree.openlink2.api.exception;

public class PlatformNotSupportedException extends AuthException {
	public PlatformNotSupportedException(String platformId) {
		super("Platform not supported: " + platformId);
	}
}

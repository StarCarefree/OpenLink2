package cn.scarefree.openlink2.api.exceptions;

public class PlatformNotSupportedException extends AuthException {
	public PlatformNotSupportedException(String platformId) {
		super("Platform not supported: " + platformId);
	}
}

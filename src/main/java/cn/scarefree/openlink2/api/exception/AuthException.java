package cn.scarefree.openlink2.api.exception;

public class AuthException extends Exception{
	public AuthException(String message) { super(message); }
	public AuthException(String message, Throwable cause) { super(message, cause); }
}

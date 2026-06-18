package cn.scarefree.openlink2.api.account;

/**
 * 登录方式
 */
public enum LoginFlowType {
	/** 使用浏览器链接进行OAuth */
	BROWSER_OAUTH,
	/** 使用设备码进行OAuth */
	DEVICE_CODE,
	/** 直接使用凭证 */
	CREDENTIALS
}

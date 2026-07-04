package cn.scarefree.openlink2.api.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 登录传递的信息
 */
@Getter
@Setter
@AllArgsConstructor
public class LoginRequestInfo {
	/**
	 * 登录的方式
	 */
	private LoginFlowType flowType;
	/**
	 * {@link LoginFlowType#BROWSER_OAUTH}使用的验证url
	 */
	private String authorizationUrl;
	/**
	 * {@link LoginFlowType#DEVICE_CODE}使用的用户code
	 */
	private String userCode;
	/**
	 * {@link LoginFlowType#DEVICE_CODE}使用的验证url
	 */
	private String verificationUri;
	/**
	 * {@link LoginFlowType#DEVICE_CODE}使用的轮询间隔的秒数
	 */
	private int intervalSeconds;
	private int expiresIn;
	private Map<String, Object> internalState;   // 内部状态传递
}

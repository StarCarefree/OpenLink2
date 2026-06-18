package cn.scarefree.openlink2.api.account;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * javadoc是一点也不想写了
 */
@Getter
@Setter
public class LoginRequestInfo {
	private LoginFlowType flowType;
	private String authorizationUrl;     // BROWSER_OAUTH 使用
	private String userCode;             // DEVICE_CODE 使用
	private String verificationUri;      // DEVICE_CODE 使用
	private int intervalSeconds;
	private int expiresIn;
	private Map<String, Object> internalState;   // 内部状态传递
}

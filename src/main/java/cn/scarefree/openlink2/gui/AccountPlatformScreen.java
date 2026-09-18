package cn.scarefree.openlink2.gui;

import cn.scarefree.openlink2.OpenLink2;
import cn.scarefree.openlink2.api.account.Account;
import cn.scarefree.openlink2.api.account.AccountManager;
import cn.scarefree.openlink2.api.account.AccountPlatform;
import cn.scarefree.openlink2.api.account.LoginFlowType;
import cn.scarefree.openlink2.api.account.LoginRequestInfo;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.task.ClientScheduler;
import net.minecraft.Util;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class AccountPlatformScreen extends AuiScreen {

	private static final String PAGE = "openlink2/accountplatform.html";
	private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final int DEFAULT_POLL_SECONDS = 5;
	private String selectedPlatformId;
	private LoginRequestInfo pendingLogin;
	private Account currentAccount;
	private ClientScheduler.Cancellable loginPoller;
	private long loginDeadline;

	public AccountPlatformScreen() {
		super(PAGE);
	}

	@Override
	protected void onPageReady(Document document) {
		if (document == null) {
			return;
		}
		renderList(document);

		Util.OS os = Util.getPlatform();
		KeyboardHandler handler = Minecraft.getInstance().keyboardHandler;
		onClick(document, "btn-close", e -> Minecraft.getInstance().execute(this::onClose));
		onClick(document, "btn-login-cancel", e -> cancelLogin());
		onClick(document, "btn-open-browser", e -> os.openUri(pendingLogin.getAuthorizationUrl()));
		onClick(document, "btn-copy-url", e -> handler.setClipboard(pendingLogin.getAuthorizationUrl()));
		onClick(document, "btn-open-verify", e -> os.openUri(pendingLogin.getVerificationUri()));
		onClick(document, "btn-copy-code", e -> handler.setClipboard(pendingLogin.getUserCode()));
		onClick(document, "btn-refresh-account", e -> refreshAccount());
		onClick(document, "btn-logout", e -> logout());
	}

	private void onClick(Document document, String id, Consumer<Event> handler) {
		Element element = document.getElementById(id);
		if (element != null) {
			element.addEventListener("click", handler);
		}
	}

	private void bindText(Document document, String containerId, String name, String value) {
		Element element = document.querySelector("#" + containerId + " [data-bind=\"" + name + "\"]");
		if (element != null) {
			element.setTextContent(value == null ? "" : value);
		}
	}

	private void panel(Document document, String state) {
		Element detail = document.getElementById("platform-detail");
		if (detail == null) {
			return;
		}
		detail.getClassList().remove("state-empty", "state-login", "state-settings");
		detail.getClassList().add("state-" + state);
	}

	private static String translatable(String key, Object... args) {
		return OpenLink2.translatable("gui.openlink2.accountplatform." + key, args).getString();
	}

	private void setStateTag(Element tag, boolean signedIn) {
		if (tag == null) {
			return;
		}
		tag.setTextContent(translatable(signedIn ? "state.logged_in" : "state.logged_out"));
		tag.getClassList().remove("tag-2-green", "tag-2-yellow");
		tag.getClassList().add(signedIn ? "tag-2-green" : "tag-2-yellow");
	}

	private void showLoginStatus(Document document, String message) {
		bindText(document, "panel-login", "login-status", message);
		Element status = document.getElementById("login-status");
		if (status != null) {
			status.getClassList().remove("hidden");
		}
	}

	private void renderList(Document document) {
		Element list = document.getElementById("platform-list");
		Element template = document.getElementById("platform-item-template");
		if (list == null || template == null) {
			return;
		}
		for (Element child : new ArrayList<>(list.children)) {
			list.removeChild(child);
		}
		Set<String> signedIn = new HashSet<>();
		for (Account account : AccountManager.getAccountManager().getAllAccounts()) {
			signedIn.add(account.getPlatformId());
		}
		for (AccountPlatform platform : AccountManager.getAccountManager().getPlatforms()) {
			String platformId = platform.getPlatformId();
			Element row = template.cloneNode(true);
			row.removeAttribute("id");
			row.setAttribute("data-platform", platformId);

			Element name = row.querySelector("[data-bind=\"platform-name\"]");
			if (name != null) {
				name.setTextContent(AccountManager.getAccountManager().getPlatform(platformId).getDisplayName());
			}
			setStateTag(row.querySelector("[data-bind=\"platform-state\"]"), signedIn.contains(platformId));

			row.addEventListener("click", e -> selectPlatform(platformId));
			list.appendChild(row);
		}

		if (selectedPlatformId != null) {
			markSelected(document, selectedPlatformId);
		}
	}

	private void markSelected(Document document, String platformId) {
		for (Element row : document.querySelectorAll(".list-group-2-item")) {
			boolean hit = platformId.equals(row.getAttribute("data-platform"));
			row.getClassList().toggle("active", hit);
			if (hit) {
				row.setAttribute("aria-selected", "true");
			} else {
				row.removeAttribute("aria-selected");
			}
		}
	}

	private void selectPlatform(String platformId) {
		selectedPlatformId = platformId;
		Document document = getLinkedDocument();
		if (document == null) {
			return;
		}
		markSelected(document, platformId);

		List<Account> accounts = AccountManager.getAccountManager().getAccounts(platformId);
		if (accounts.isEmpty()) {
			startLogin();
		} else {
			// TODO 多账号：现在只显示第一个，以后加账号切换（页面得加个账号列表）
			fillAccount(accounts.get(0));
		}
	}

	private void startLogin() {
		Document document = getLinkedDocument();
		if (document == null || selectedPlatformId == null) {
			return;
		}
		panel(document, "login");

		AccountManager.getAccountManager().login(selectedPlatformId)
				.thenAccept(info -> Minecraft.getInstance().execute(() -> fillLogin(info)))
				.exceptionally(error -> {
					Minecraft.getInstance().execute(() -> {
						Document current = getLinkedDocument();
						if (current != null) {
							showLoginStatus(current, translatable("login.failed", String.valueOf(error.getMessage())));
						}
					});
					return null;
				});
	}

	private void fillLogin(LoginRequestInfo info) {
		pendingLogin = info;
		Document document = getLinkedDocument();
		if (document == null) {
			return;
		}

		boolean device = info.getFlowType() == LoginFlowType.DEVICE_CODE;
		Element login = document.getElementById("panel-login");
		if (login != null) {
			login.setAttribute("data-flow", device ? "device" : "browser");
		}

		if (device) {
			bindText(document, "panel-login", "user-code", info.getUserCode());
			bindText(document, "panel-login", "verify-url", info.getVerificationUri());
			bindText(document, "panel-login", "poll-interval", String.valueOf(info.getIntervalSeconds()));
		} else {
			bindText(document, "panel-login", "auth-url", info.getAuthorizationUrl());
		}

		Element status = document.getElementById("login-status");
		if (status != null) {
			status.getClassList().remove("hidden");
		}

		startLoginPoll(info);
	}

	private void startLoginPoll(LoginRequestInfo info) {
		stopLoginPoll();

		int seconds = info.getIntervalSeconds() > 0 ? info.getIntervalSeconds() : DEFAULT_POLL_SECONDS;
		loginDeadline = info.getExpiresIn() > 0
				? System.currentTimeMillis() + info.getExpiresIn() * 1000L
				: 0;

		loginPoller = ClientScheduler.setInterval(seconds * 1000, task -> {
			if (loginDeadline > 0 && System.currentTimeMillis() > loginDeadline) {
				stopLoginPoll();
				Document document = getLinkedDocument();
				if (document != null) {
					showLoginStatus(document, translatable("login.timeout"));
				}
				return;
			}
			completeLogin();
		});
	}

	private void completeLogin() {
		if (pendingLogin == null || selectedPlatformId == null) {
			stopLoginPoll();
			return;
		}

		AccountManager.getAccountManager().completeLogin(selectedPlatformId, pendingLogin)
				.thenAccept(account -> Minecraft.getInstance().execute(() -> {
					stopLoginPoll();
					pendingLogin = null;
					loginDeadline = 0;
					fillAccount(account);

					Document document = getLinkedDocument();
					if (document != null) {
						renderList(document);
					}
				}))
				.exceptionally(error -> {
					OpenLink2.LOGGER.debug("completeLogin not done yet: {}", error.getMessage());
					return null;
				});
	}

	private void stopLoginPoll() {
		if (loginPoller != null) {
			loginPoller.cancel();
			loginPoller = null;
		}
	}

	private void cancelLogin() {
		stopLoginPoll();
		pendingLogin = null;
		loginDeadline = 0;

		Document document = getLinkedDocument();
		if (document == null) {
			return;
		}
		bindText(document, "panel-login", "auth-url", "");
		bindText(document, "panel-login", "user-code", "");
		bindText(document, "panel-login", "verify-url", "");

		Element status = document.getElementById("login-status");
		if (status != null) {
			status.getClassList().add("hidden");
		}
		panel(document, "empty");
	}

	private void refreshAccount() {
		if (selectedPlatformId == null || currentAccount == null) {
			return;
		}
		String platformUserId = currentAccount.getPlatformUserId();

		AccountManager.getAccountManager().ensureFreshAccount(selectedPlatformId, platformUserId)
				.thenAccept(account -> Minecraft.getInstance().execute(() -> fillAccount(account)))
				.exceptionally(error -> {
					OpenLink2.LOGGER.warn("Failed to refresh account {}/{}", selectedPlatformId, platformUserId, error);
					return null;
				});
	}

	private void logout() {
		if (selectedPlatformId == null || currentAccount == null) {
			return;
		}
		try {
			AccountManager.getAccountManager().removeAccount(selectedPlatformId, currentAccount.getPlatformUserId());
		} catch (Exception e) {
			OpenLink2.LOGGER.warn("Failed to remove account {}/{}", selectedPlatformId, currentAccount.getPlatformUserId(), e);
			return;
		}

		currentAccount = null;
		Document document = getLinkedDocument();
		if (document != null) {
			renderList(document);
		}
		selectPlatform(selectedPlatformId);
	}

	private void fillAccount(Account account) {
		Document document = getLinkedDocument();
		if (document == null) {
			return;
		}
		currentAccount = account;

		bindText(document, "panel-settings", "display-name", account.getDisplayName());
		bindText(document, "panel-settings", "platform-user-id", account.getPlatformUserId());
		bindText(document, "panel-settings", "email", account.getEmail());

		String expires = EXPIRES_FORMAT.format(
				Instant.ofEpochMilli(account.getExpiresAt()).atZone(ZoneId.systemDefault()));
		bindText(document, "panel-settings", "expires-at",
				account.isExpired() ? expires + translatable("info.expired") : expires);

		setStateTag(document.querySelector("#panel-settings [data-bind=\"account-state\"]"), true);
		panel(document, "settings");
	}
}

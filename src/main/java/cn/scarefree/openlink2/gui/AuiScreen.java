package cn.scarefree.openlink2.gui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.task.ClientScheduler;

/**
 * AUI 界面基类
 */
public abstract class AuiScreen extends ApricityScreen {

	private static final int REFRESH_POLL_MS = 1000;

	private ClientScheduler.Cancellable watcher;
	private long lastGeneration = -1;

	protected AuiScreen(String page) {
		super(page);
	}

	/** 页面创建 / refresh 后回调，可能跑多次，要幂等 */
	protected abstract void onPageReady(Document document);

	@Override
	protected void init() {
		super.init();
		startWatcher();
		onPageReady(getLinkedDocument());
	}

	@Override
	public void removed() {
		cancelWatcher();
		super.removed();
	}

	private void startWatcher() {
		if (watcher != null) {
			return;
		}
		watcher = ClientScheduler.setInterval(REFRESH_POLL_MS, task -> {
			Document document = getLinkedDocument();
			if (document == null) {
				cancelWatcher();
				return;
			}
			long generation = document.getRefreshGeneration();
			if (generation != lastGeneration) {
				lastGeneration = generation;
				onPageReady(document);
			}
		});
	}

	private void cancelWatcher() {
		if (watcher != null) {
			watcher.cancel();
			watcher = null;
		}
		lastGeneration = -1;
	}
}

package cn.scarefree.openlink2.gui;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.gui.screens.Screen;

public class OpenLinkScreen extends ApricityScreen {

	private final Screen parent;

	protected OpenLinkScreen(String path, Screen parent) {
		super(path);
		this.setPauseGame(true);
		this.setShowDefaultBackground(false);
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		onClick("btn-back", this::onClose);
	}

	@Override
	public void onClose() {
		super.onClose();
		this.minecraft.setScreen(parent);
	}

	protected void setText(String id, String text) {
		Element e = this.getLinkedDocument().getElementById(id);
		if (e != null) e.setTextContent(text == null || text.isBlank() ? "—" : text);
	}

	protected void setVisible(String id, boolean visible) {
		Element e = this.getLinkedDocument().getElementById(id);
		if (e != null) e.getClassList().toggle("is-hidden", !visible);
	}

	protected void onClick(String id, Runnable action) {
		Element e = this.getLinkedDocument().getElementById(id);
		if (e != null) e.addEventListener("click", ev -> action.run());
	}
}

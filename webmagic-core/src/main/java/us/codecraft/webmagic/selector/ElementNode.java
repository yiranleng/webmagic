package us.codecraft.webmagic.selector;

import org.jsoup.nodes.Element;

public class ElementNode {
	private String html;
	private Element element;

	public ElementNode() {
	}
	
	public ElementNode(String html, Element element) {
		this.html = html;
		this.element = element;
	}

	public String getHtml() {
		return html;
	}

	public void setHtml(String html) {
		this.html = html;
	}

	public Element getElement() {
		return element;
	}

	public void setElement(Element element) {
		this.element = element;
	}
}

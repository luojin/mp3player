package com.alex.media;

public class LyricBean {
	private long beginTime=0;
	private long sleepTime =0;
	private String lrcBody = null;

	public long getBeginTime() {
		return beginTime;
	}
	public void setBeginTime(long beginTime) {
		this.beginTime = beginTime;
	}
	public long getSleepTime() {
		return sleepTime;
	}
	public void setSleepTime(long sleepTime) {
		this.sleepTime = sleepTime;
	}
	public String getLrcBody() {
		return lrcBody;
	}
	public void setLrcBody(String lrcBody) {
		this.lrcBody = lrcBody;
	}
}

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.eclipse.jetty.server;

public class PostContext {
    private boolean isObsPostRequest = false;
    private String obsPostBoundary;
    private byte[] postBuf;
    private byte[] postLineBuf;
    private int postBufCount = 0;
    private int postBufPos = 0;
    private int postLineBufCount = 0;
    private int postLineBufPos = 0;
    private boolean isPostEof = false;

    public PostContext() {
    }

    public boolean isObsPostRequest() {
        return this.isObsPostRequest;
    }

    public void setObsPostRequest(boolean obsPostRequest) {
        this.isObsPostRequest = obsPostRequest;
    }

    public String getObsPostBoundary() {
        return this.obsPostBoundary;
    }

    public void setObsPostBoundary(String obsPostBoundary) {
        this.obsPostBoundary = obsPostBoundary;
    }

    public byte[] getPostBuf() {
        return this.postBuf;
    }

    public void setPostBuf(byte[] postBuf) {
        this.postBuf = postBuf;
    }

    public byte[] getPostLineBuf() {
        return this.postLineBuf;
    }

    public void setPostLineBuf(byte[] postLineBuf) {
        this.postLineBuf = postLineBuf;
    }

    public int getPostBufCount() {
        return this.postBufCount;
    }

    public void setPostBufCount(int postBufCount) {
        this.postBufCount = postBufCount;
    }

    public void addToPostBufCount(int adjust) {
        this.postBufCount += adjust;
    }

    public void subFromPostBufCount(int adjust) {
        this.postBufCount -= adjust;
    }

    public int getPostBufPos() {
        return this.postBufPos;
    }

    public void setPostBufPos(int postBufPos) {
        this.postBufPos = postBufPos;
    }

    public void addToPostBufPos(int adjust) {
        this.postBufPos += adjust;
    }

    public void subFromPostBufPos(int adjust) {
        this.postBufPos -= adjust;
    }

    public int getPostLineBufCount() {
        return this.postLineBufCount;
    }

    public void setPostLineBufCount(int postLineBufCount) {
        this.postLineBufCount = postLineBufCount;
    }

    public void addToPostLineBufCount(int adjust) {
        this.postLineBufCount += adjust;
    }

    public void subFromPostLineBufCount(int adjust) {
        this.postLineBufCount -= adjust;
    }

    public int getPostLineBufPos() {
        return this.postLineBufPos;
    }

    public void setPostLineBufPos(int postLineBufPos) {
        this.postLineBufPos = postLineBufPos;
    }

    public void addToPostLineBufPos(int adjust) {
        this.postLineBufPos += adjust;
    }

    public void subFromPostLineBufPos(int adjust) {
        this.postLineBufPos -= adjust;
    }

    public boolean isPostEof() {
        return this.isPostEof;
    }

    public void setPostEof(boolean postEof) {
        this.isPostEof = postEof;
    }
}

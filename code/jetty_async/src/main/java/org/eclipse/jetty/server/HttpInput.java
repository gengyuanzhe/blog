//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.eclipse.jetty.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import org.eclipse.jetty.http.BadMessageException;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.Destroyable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.Invocable;

import uds.common.entity.eio.ProxyByteBuffer;

public class HttpInput extends ServletInputStream implements Runnable {
    private static final Logger LOG = Log.getLogger(HttpInput.class);
    static final Content EOF_CONTENT = new EofContent("EOF");
    static final Content EARLY_EOF_CONTENT = new EofContent("EARLY_EOF");
    private final byte[] _oneByteBuffer = new byte[1];
    private Content _content;
    private Content _intercepted;
    private final Deque<Content> _inputQ = new ArrayDeque();
    private final HttpChannelState _channelState;
    private ReadListener _listener;
    private State _state;
    private long _firstByteTimeStamp;
    private long _contentArrived;
    private long _contentConsumed;
    private long _blockUntil;
    private boolean _waitingForContent;
    private Interceptor _interceptor;
    private static ThreadLocal<ProxyByteBuffer> threadLocalSglBuffer = new ThreadLocal();
    private static ThreadLocal<InputStream> httpInputSteamHolder = new ThreadLocal();
    private static ThreadLocal<PostContext> postContextHolder = new ThreadLocal();
    private static ThreadLocal<MessageDigest> messageDigestHolder = new ThreadLocal();
    private static final int CRLF_LEN = 2;
    protected static final State STREAM = new State() {
        public boolean blockForContent(HttpInput input) throws IOException {
            input.blockForContent();
            return true;
        }

        public String toString() {
            return "STREAM";
        }
    };
    protected static final State ASYNC = new State() {
        public int noContent() {
            return 0;
        }

        public String toString() {
            return "ASYNC";
        }
    };
    protected static final State EARLY_EOF = new EOFState() {
        public int noContent() throws IOException {
            throw this.getError();
        }

        public String toString() {
            return "EARLY_EOF";
        }

        public IOException getError() {
            return new EofException("Early EOF");
        }
    };
    protected static final State EOF = new EOFState() {
        public String toString() {
            return "EOF";
        }
    };
    protected static final State AEOF = new EOFState() {
        public String toString() {
            return "AEOF";
        }
    };

    public HttpInput(HttpChannelState state) {
        this._state = STREAM;
        this._firstByteTimeStamp = -1L;
        this._channelState = state;
    }

    protected HttpChannelState getHttpChannelState() {
        return this._channelState;
    }

    public static void setThreadLocalSglBuffer(ProxyByteBuffer sglByteBuffer) {
        threadLocalSglBuffer.set(sglByteBuffer);
    }

    public static ProxyByteBuffer getThreadLocalSglBuffer() {
        return (ProxyByteBuffer)threadLocalSglBuffer.get();
    }

    public static void setHttpInputStream(InputStream inputStream) {
        httpInputSteamHolder.set(inputStream);
    }

    public static InputStream getHttpInputStream() {
        return (InputStream)httpInputSteamHolder.get();
    }

    public static PostContext getPostContext() {
        PostContext postContext = (PostContext)postContextHolder.get();
        if (postContext == null) {
            postContext = new PostContext();
            postContextHolder.set(postContext);
        }

        return postContext;
    }

    public static void setIsObsPostRequest(boolean isObsPostRequest) {
        getPostContext().setObsPostRequest(isObsPostRequest);
    }

    public static void cleanPostContext() {
        PostContext postContext = new PostContext();
        postContextHolder.set(postContext);
    }

    public static MessageDigest getMessageDigest() {
        return (MessageDigest)messageDigestHolder.get();
    }

    public static void setMessageDigest(MessageDigest messageDigest) {
        messageDigestHolder.set(messageDigest);
    }

    public static void setObsPostBoundary(String partBboundary) {
        getPostContext().setObsPostBoundary(partBboundary);
    }

    public static void setPostBuf(byte[] buf) {
        getPostContext().setPostBuf(buf);
    }

    public static void setPostLineBuf(byte[] buf) {
        getPostContext().setPostLineBuf(buf);
    }

    public void recycle() {
        synchronized(this._inputQ) {
            Throwable failure = this.fail(this._intercepted, (Throwable)null);
            this._intercepted = null;
            failure = this.fail(this._content, failure);
            this._content = null;

            for(Content item = (Content)this._inputQ.poll(); item != null; item = (Content)this._inputQ.poll()) {
                failure = this.fail(item, failure);
            }

            this._listener = null;
            this._state = STREAM;
            this._contentArrived = 0L;
            this._contentConsumed = 0L;
            this._firstByteTimeStamp = -1L;
            this._blockUntil = 0L;
            this._waitingForContent = false;
            if (this._interceptor instanceof Destroyable) {
                ((Destroyable)this._interceptor).destroy();
            }

            this._interceptor = null;
        }
    }

    private Throwable fail(Content content, Throwable failure) {
        if (content != null) {
            if (failure == null) {
                failure = new IOException("unconsumed input");
            }

            content.failed((Throwable)failure);
        }

        return (Throwable)failure;
    }

    public Interceptor getInterceptor() {
        return this._interceptor;
    }

    public void setInterceptor(Interceptor interceptor) {
        this._interceptor = interceptor;
    }

    public void addInterceptor(Interceptor interceptor) {
        if (this._interceptor == null) {
            this._interceptor = interceptor;
        } else {
            this._interceptor = new ChainedInterceptor(this._interceptor, interceptor);
        }

    }

    public boolean isReadEof() throws IOException {
        int available;
        if (getPostContext().isObsPostRequest()) {
            available = getPostContext().getPostBufCount() - getPostContext().getPostBufPos();
            if (available > 0) {
                return false;
            }
        }

        available = 0;
        boolean woken = false;
        synchronized(this._inputQ) {
            if (this._content == null) {
                this._content = (Content)this._inputQ.poll();
            }

            while(this._content == null) {
                try {
                    this._content = this.nextContent();
                    if (this._content != null) {
                        break;
                    }

                    if (!this._state.blockForContent(this)) {
                        available = this._state.noContent();
                        if (available < 0) {
                            woken = this._channelState.onReadEof();
                        }
                        break;
                    }
                } catch (Throwable var6) {
                    woken = this.failed(var6);
                    if (woken) {
                        this.wake();
                    }

                    throw var6;
                }

                if (this._content == null) {
                    this._content = (Content)this._inputQ.poll();
                }
            }

            if (this._content != null) {
                available = this._content.remaining();
            }
        }

        if (woken) {
            this.wake();
        }

        return available == 0;
    }

    public int available() {
        int available;
        if (getPostContext().isObsPostRequest()) {
            available = getPostContext().getPostBufCount() - getPostContext().getPostBufPos();
            if (available > 0) {
                return available;
            }
        }

        available = 0;
        boolean woken = false;
        synchronized(this._inputQ) {
            if (this._content == null) {
                this._content = (Content)this._inputQ.poll();
            }

            if (this._content == null) {
                try {
                    this.produceContent();
                } catch (Throwable var6) {
                    woken = this.failed(var6);
                }

                if (this._content == null) {
                    this._content = (Content)this._inputQ.poll();
                }
            }

            if (this._content != null) {
                available = this._content.remaining();
            }
        }

        if (woken) {
            this.wake();
        }

        return available;
    }

    protected void wake() {
        HttpChannel channel = this._channelState.getHttpChannel();
        Executor executor = channel.getConnector().getServer().getThreadPool();
        executor.execute(channel);
    }

    private long getBlockingTimeout() {
        return this.getHttpChannelState().getHttpChannel().getHttpConfiguration().getBlockingTimeout();
    }

    public int read() throws IOException {
        int read = this.read(this._oneByteBuffer, 0, 1);
        if (read == 0) {
            throw new IllegalStateException("unready read=0");
        } else {
            return read < 0 ? -1 : this._oneByteBuffer[0] & 255;
        }
    }

    public int read(byte[] b, int off, int len) throws IOException {
        httpInputSteamHolder.set(this);
        Boolean wake = false;
        int l = 0;
        synchronized(this._inputQ) {
            long minRequestDataRate;
            if (!this.isAsync() && this._blockUntil == 0L) {
                minRequestDataRate = this.getBlockingTimeout();
                if (minRequestDataRate > 0L) {
                    this._blockUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(minRequestDataRate);
                }
            }

            minRequestDataRate = this._channelState.getHttpChannel().getHttpConfiguration().getMinRequestDataRate();
            if (minRequestDataRate > 0L && this._firstByteTimeStamp != -1L) {
                long period = System.nanoTime() - this._firstByteTimeStamp;
                if (period > 0L) {
                    long minimumData = minRequestDataRate * TimeUnit.NANOSECONDS.toMillis(period) / TimeUnit.SECONDS.toMillis(1L);
                    if (this._contentArrived < minimumData) {
                        BadMessageException bad = new BadMessageException(408, String.format("Request content data rate < %d B/s", minRequestDataRate));
                        if (this._channelState.isResponseCommitted()) {
                            this._channelState.getHttpChannel().abort(bad);
                        }

                        throw bad;
                    }
                }
            }

            boolean isCommonRead = true;
            if (threadLocalSglBuffer.get() != null && getPostContext().isObsPostRequest()) {
                l = this.getFromPart((ProxyByteBuffer)threadLocalSglBuffer.get(), off, len, wake);
                isCommonRead = false;
            }

            while(isCommonRead) {
                Content item = this.nextContent();
                if (item != null) {
                    if (b == null && threadLocalSglBuffer.get() != null) {
                        l = this.get(item, (ProxyByteBuffer)threadLocalSglBuffer.get(), off, len);
                    } else {
                        l = this.get(item, b, off, len);
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} read {} from {}", new Object[]{this, l, item});
                    }

                    if (item.isEmpty()) {
                        this.nextInterceptedContent();
                    }
                    break;
                } else if (!this._state.blockForContent(this)) {
                    l = this._state.noContent();
                    if (l < 0) {
                        wake = this._channelState.onReadEof();
                    }
                    break;
                }
            }
        }

        if (wake) {
            this.wake();
        }

        return l;
    }

    protected void produceContent() throws IOException {
    }

    public void asyncReadProduce() throws IOException {
        synchronized(this._inputQ) {
            this.produceContent();
        }
    }


    private static AlarmIn alarm = null;

    public static void registerAlarm(AlarmIn alarm) {
        HttpInput.alarm = alarm;
    }

    public void doAlarm() {
        if (alarm == null) {
            return;
        } else {
            alarm.alarm("123");
        }
    }

    protected Content nextContent() throws IOException {
        Content content = this.nextNonSentinelContent();
        if (content == null && !this.isFinished()) {
            this.produceContent();
            content = this.nextNonSentinelContent();
        }

        return content;
    }

    protected Content nextNonSentinelContent() throws IOException {
        while(true) {
            Content content = this.nextInterceptedContent();
            if (!(content instanceof SentinelContent)) {
                return content;
            }

            this.consume(content);
        }
    }

    protected Content produceNextContent() throws IOException {
        Content content = this.nextInterceptedContent();
        if (content == null && !this.isFinished()) {
            this.produceContent();
            content = this.nextInterceptedContent();
        }

        return content;
    }

    protected Content nextInterceptedContent() throws IOException {
        if (this._intercepted != null) {
            if (this._intercepted.hasContent()) {
                return this._intercepted;
            }

            this._intercepted.succeeded();
            this._intercepted = null;
        }

        if (this._content == null) {
            this._content = (Content)this._inputQ.poll();
        }

        while(this._content != null) {
            if (this._interceptor != null) {
                this._intercepted = this.intercept(this._content);
                if (this._intercepted != null && this._intercepted != this._content) {
                    if (this._intercepted.hasContent()) {
                        return this._intercepted;
                    }

                    this._intercepted.succeeded();
                }

                this._intercepted = null;
            }

            if (this._content.hasContent() || this._content instanceof SentinelContent) {
                return this._content;
            }

            this._content.succeeded();
            this._content = (Content)this._inputQ.poll();
        }

        return null;
    }

    private Content intercept(Content content) throws IOException {
        try {
            return this._interceptor.readFrom(content);
        } catch (Throwable var6) {
            IOException failure = new IOException("Bad content", var6);
            content.failed(failure);
            HttpChannel channel = this._channelState.getHttpChannel();
            Response response = channel.getResponse();
            if (response.isCommitted()) {
                channel.abort(failure);
            }

            throw failure;
        }
    }

    private void consume(Content content) {
        if (!this.isError() && content instanceof EofContent) {
            if (content == EARLY_EOF_CONTENT) {
                this._state = EARLY_EOF;
            } else if (this._listener == null) {
                this._state = EOF;
            } else {
                this._state = AEOF;
            }
        }

        content.succeeded();
        if (this._content == content) {
            this._content = null;
        } else if (this._intercepted == content) {
            this._intercepted = null;
        }

    }

    protected int get(Content content, ProxyByteBuffer buffer, int offset, int length) {
        int l = content.get(buffer, offset, length);
        this._contentConsumed += (long)l;
        return l;
    }

    protected int getFromPart(ProxyByteBuffer buffer, int offset, int length, Boolean wake) throws IOException {
        if (length == 0) {
            return 0;
        } else {
            int total = 0;
            int avail = getPostContext().getPostBufCount() - getPostContext().getPostBufPos() - 2;
            if (avail <= 0) {
                this.fillPostBuf(wake);
                avail = getPostContext().getPostBufCount() - getPostContext().getPostBufPos() - 2;
                if (avail <= 0) {
                    return -1;
                }
            }

            byte[] postBuf = getPostContext().getPostBuf();
            int copy = Math.min(length, avail);
            buffer.put(postBuf, getPostContext().getPostBufPos(), copy);
            MessageDigest sha256Digest = (MessageDigest)messageDigestHolder.get();
            if (sha256Digest != null) {
                sha256Digest.update(postBuf, getPostContext().getPostBufPos(), copy);
            }

            total += copy;
            getPostContext().addToPostBufPos(copy);

            while(total < length) {
                this.fillPostBuf(wake);
                avail = getPostContext().getPostBufCount() - getPostContext().getPostBufPos() - 2;
                if (avail <= 0) {
                    return total;
                }

                copy = Math.min(length - total, avail);
                buffer.put(postBuf, getPostContext().getPostBufPos(), copy);
                if (sha256Digest != null) {
                    sha256Digest.update(postBuf, getPostContext().getPostBufPos(), copy);
                }

                total += copy;
                getPostContext().addToPostBufPos(copy);
            }

            return total;
        }
    }

    private void fillPostBuf(Boolean wake) throws IOException {
        if (!getPostContext().isPostEof()) {
            byte[] postBuf = getPostContext().getPostBuf();
            if (getPostContext().getPostBufCount() > 0) {
                if (getPostContext().getPostBufCount() - getPostContext().getPostBufPos() != 2) {
                    throw new IllegalStateException("The buf is illegal.");
                }

                System.arraycopy(postBuf, getPostContext().getPostBufPos(), postBuf, 0, getPostContext().getPostBufCount() - getPostContext().getPostBufPos());
                getPostContext().subFromPostBufCount(getPostContext().getPostBufPos());
                getPostContext().setPostBufPos(0);
            }

            String obsPostBoundary = getPostContext().getObsPostBoundary();
            if (obsPostBoundary == null) {
                throw new IOException("Separation boundary was not specified");
            } else {
                int boundaryLen = obsPostBoundary.length();

                int readCount;
                for(int maxReadCount = postBuf.length - boundaryLen - 2; getPostContext().getPostBufCount() < maxReadCount; getPostContext().addToPostBufCount(readCount)) {
                    readCount = this.readPostLine(postBuf, getPostContext().getPostBufCount(), postBuf.length - getPostContext().getPostBufCount(), wake);
                    if (-1 == readCount) {
                        throw new IOException("Read the end, but is not expected.");
                    }

                    if (readCount >= boundaryLen) {
                        getPostContext().setPostEof(true);

                        for(int index = 0; index < boundaryLen; ++index) {
                            if (postBuf[getPostContext().getPostBufCount() + index] != obsPostBoundary.charAt(index)) {
                                getPostContext().setPostEof(false);
                                break;
                            }
                        }

                        if (getPostContext().isPostEof()) {
                            break;
                        }
                    }
                }

            }
        }
    }

    public int readPostLine(byte[] data, int off, int length, Boolean wake) throws IOException {
        int total = 0;
        if (length == 0) {
            return 0;
        } else {
            int availlen = getPostContext().getPostLineBufCount() - getPostContext().getPostLineBufPos();
            if (availlen <= 0) {
                this.fillPostLineBuf(wake);
                availlen = getPostContext().getPostLineBufCount() - getPostContext().getPostLineBufPos();
                if (availlen <= 0) {
                    return -1;
                }
            }

            int copy = Math.min(length, availlen);
            byte[] postLineBuf = getPostContext().getPostLineBuf();
            int eolpos = findeol(postLineBuf, getPostContext().getPostLineBufPos(), copy);
            if (eolpos != -1) {
                copy = eolpos;
            }

            System.arraycopy(postLineBuf, getPostContext().getPostLineBufPos(), data, off, copy);
            getPostContext().addToPostLineBufPos(copy);

            for(total += copy; total < length && eolpos == -1; total += copy) {
                this.fillPostLineBuf(wake);
                availlen = getPostContext().getPostLineBufCount() - getPostContext().getPostLineBufPos();
                if (availlen <= 0) {
                    return total;
                }

                copy = Math.min(length - total, availlen);
                eolpos = findeol(postLineBuf, getPostContext().getPostLineBufPos(), copy);
                if (eolpos != -1) {
                    copy = eolpos;
                }

                System.arraycopy(postLineBuf, getPostContext().getPostLineBufPos(), data, off + total, copy);
                getPostContext().addToPostLineBufPos(copy);
            }

            return total;
        }
    }

    private void fillPostLineBuf(Boolean wake) throws IOException {
        while(true) {
            Content item = this.nextContent();
            int index;
            if (item != null) {
                byte[] postLineBuf = getPostContext().getPostLineBuf();
                index = this.get(item, (byte[])postLineBuf, 0, postLineBuf.length);
                if (index > 0) {
                    getPostContext().setPostLineBufPos(0);
                    getPostContext().setPostLineBufCount(index);
                }

                if (item.isEmpty()) {
                    this.nextInterceptedContent();
                }
            } else {
                if (this._state.blockForContent(this)) {
                    continue;
                }

                index = this._state.noContent();
                if (index < 0) {
                    wake = this._channelState.onReadEof();
                }
            }

            return;
        }
    }

    private static int findeol(byte[] data, int pos, int len) {
        int end = pos + len;
        int index = pos;

        do {
            if (index >= end) {
                return -1;
            }
        } while(data[index++] != 10);

        return index - pos;
    }

    protected int get(Content content, byte[] buffer, int offset, int length) {
        int l = content.get(buffer, offset, length);
        this._contentConsumed += (long)l;
        return l;
    }

    protected void blockForContent() throws IOException {
        try {
            this._waitingForContent = true;
            this._channelState.getHttpChannel().onBlockWaitForContent();
            boolean loop = false;
            long timeout = 0L;

            while(true) {
                if (this._blockUntil != 0L) {
                    timeout = TimeUnit.NANOSECONDS.toMillis(this._blockUntil - System.nanoTime());
                    if (timeout <= 0L) {
                        throw new TimeoutException(String.format("Blocking timeout %d ms", this.getBlockingTimeout()));
                    }
                }

                if (loop) {
                    break;
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} blocking for content timeout={}", new Object[]{this, timeout});
                }

                if (timeout > 0L) {
                    this._inputQ.wait(timeout);
                } else {
                    this._inputQ.wait();
                }

                loop = true;
            }
        } catch (Throwable var4) {
            this._channelState.getHttpChannel().onBlockWaitForContentFailure(var4);
        }

    }

    public boolean addContent(Content content) {
        synchronized(this._inputQ) {
            this._waitingForContent = false;
            if (this._firstByteTimeStamp == -1L) {
                this._firstByteTimeStamp = System.nanoTime();
            }

            if (this.isFinished()) {
                Throwable failure = this.isError() ? this._state.getError() : new EOFException("Content after EOF");
                content.failed((Throwable)failure);
                return false;
            } else {
                this._contentArrived += (long)content.remaining();
                if (this._content == null && this._inputQ.isEmpty()) {
                    this._content = content;
                } else {
                    this._inputQ.offer(content);
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} addContent {}", new Object[]{this, content});
                }

                boolean var10000;
                try {
                    if (this.nextInterceptedContent() != null) {
                        var10000 = this.wakeup();
                        return var10000;
                    }

                    var10000 = false;
                } catch (Throwable var5) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("", var5);
                    }

                    return this.failed(var5);
                }

                return var10000;
            }
        }
    }

    public boolean hasContent() {
        synchronized(this._inputQ) {
            return this._content != null || this._inputQ.size() > 0;
        }
    }

    public void unblock() {
        synchronized(this._inputQ) {
            this._inputQ.notify();
        }
    }

    public long getContentConsumed() {
        synchronized(this._inputQ) {
            return this._contentConsumed;
        }
    }

    public long getContentReceived() {
        synchronized(this._inputQ) {
            return this._contentArrived;
        }
    }

    public boolean earlyEOF() {
        return this.addContent(EARLY_EOF_CONTENT);
    }

    public boolean eof() {
        return this.addContent(EOF_CONTENT);
    }

    public boolean consumeAll() {
        while(true) {
            synchronized(this._inputQ) {
                if (this._intercepted != null) {
                    this._intercepted.skip(this._intercepted.remaining());
                    this.consume(this._intercepted);
                }

                if (this._content != null) {
                    this._content.skip(this._content.remaining());
                    this.consume(this._content);
                }

                for(Content content = (Content)this._inputQ.poll(); content != null; content = (Content)this._inputQ.poll()) {
                    this.consume(content);
                }

                if (this._state instanceof EOFState) {
                    return !(this._state instanceof ErrorState);
                }

                try {
                    this.produceContent();
                    if (this._content == null && this._intercepted == null && this._inputQ.isEmpty()) {
                        this._state = EARLY_EOF;
                        this._inputQ.notify();
                        boolean var10000 = false;
                        return var10000;
                    }
                } catch (Throwable var5) {
                    LOG.debug(var5);
                    this._state = new ErrorState(var5);
                    this._inputQ.notify();
                    return false;
                }
            }
        }
    }

    public boolean isError() {
        synchronized(this._inputQ) {
            return this._state instanceof ErrorState;
        }
    }

    public boolean isAsync() {
        synchronized(this._inputQ) {
            return this._state == ASYNC;
        }
    }

    public boolean isFinished() {
        synchronized(this._inputQ) {
            return this._state instanceof EOFState;
        }
    }

    public boolean isReady() {
        synchronized(this._inputQ) {
            boolean var10000;
            try {
                if (this._listener == null) {
                    var10000 = true;
                    return var10000;
                }

                if (this._state instanceof EOFState) {
                    var10000 = true;
                    return var10000;
                }

                if (!this._waitingForContent) {
                    if (this.produceNextContent() != null) {
                        var10000 = true;
                        return var10000;
                    }

                    this._channelState.onReadUnready();
                    this._waitingForContent = true;
                    var10000 = false;
                    return var10000;
                }

                var10000 = false;
            } catch (Throwable var4) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("", var4);
                }

                this.failed(var4);
                return true;
            }

            return var10000;
        }
    }

    public void setReadListener(ReadListener readListener) {
        boolean woken = false;
        synchronized(this._inputQ) {
            try {
                if (this._listener != null) {
                    throw new IllegalStateException("ReadListener already set");
                }

                this._listener = (ReadListener)Objects.requireNonNull(readListener);
                if (this.isError()) {
                    woken = this._channelState.onReadReady();
                } else {
                    Content content = this.produceNextContent();
                    if (content != null) {
                        this._state = ASYNC;
                        woken = this._channelState.onReadReady();
                    } else if (this._state == EOF) {
                        this._state = AEOF;
                        woken = this._channelState.onReadEof();
                    } else {
                        this._state = ASYNC;
                        this._channelState.onReadUnready();
                        this._waitingForContent = true;
                    }
                }
            } catch (Throwable var6) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("", var6);
                }

                this.failed(var6);
                woken = this._channelState.onReadReady();
            }
        }

        if (woken) {
            this.wake();
        }

    }

    public boolean onIdleTimeout(Throwable x) {
        synchronized(this._inputQ) {
            boolean neverDispatched = this.getHttpChannelState().isIdle();
            if ((this._waitingForContent || neverDispatched) && !this.isError()) {
                x.addSuppressed(new Throwable("HttpInput idle timeout"));
                this._state = new ErrorState(x);
                return this.wakeup();
            } else {
                return false;
            }
        }
    }

    public boolean failed(Throwable x) {
        synchronized(this._inputQ) {
            if (this.isError()) {
                if (LOG.isDebugEnabled()) {
                    Throwable failure = new Throwable(this._state.getError());
                    failure.addSuppressed(x);
                    LOG.debug(failure);
                }
            } else {
                x.addSuppressed(new Throwable("HttpInput failure"));
                this._state = new ErrorState(x);
            }

            return this.wakeup();
        }
    }

    private boolean wakeup() {
        if (this._listener != null) {
            return this._channelState.onContentAdded();
        } else {
            this._inputQ.notify();
            return false;
        }
    }

    public void run() {
        ReadListener listener = null;
        Throwable error = null;
        boolean aeof = false;

        try {
            synchronized(this._inputQ) {
                listener = this._listener;
                if (this._state == EOF) {
                    return;
                }

                if (this._state == AEOF) {
                    this._state = EOF;
                    aeof = true;
                }

                error = this._state.getError();
                if (!aeof && error == null) {
                    Content content = this.nextInterceptedContent();
                    if (content == null) {
                        return;
                    }

                    if (content instanceof EofContent) {
                        this.consume(content);
                        if (this._state == EARLY_EOF) {
                            error = this._state.getError();
                        } else if (this._state == AEOF) {
                            aeof = true;
                            this._state = EOF;
                        }
                    }
                }
            }

            if (error != null) {
                this._channelState.getHttpChannel().getResponse().getHttpFields().add(HttpConnection.CONNECTION_CLOSE);
                listener.onError(error);
            } else if (aeof) {
                listener.onAllDataRead();
            } else {
                listener.onDataAvailable();
            }
        } catch (Throwable var9) {
            Throwable e = var9;
            LOG.warn(var9.toString(), new Object[0]);
            if (LOG.isDebugEnabled()) {
                LOG.debug("", var9);
            }

            try {
                if (aeof || error == null) {
                    this._channelState.getHttpChannel().getResponse().getHttpFields().add(HttpConnection.CONNECTION_CLOSE);
                    listener.onError(e);
                }
            } catch (Throwable var7) {
                LOG.warn(var7.toString(), new Object[0]);
                LOG.debug(var7);
                throw new RuntimeIOException(var7);
            }
        }

    }

    public String toString() {
        State state;
        long consumed;
        int q;
        Content content;
        synchronized(this._inputQ) {
            state = this._state;
            consumed = this._contentConsumed;
            q = this._inputQ.size();
            content = (Content)this._inputQ.peekFirst();
        }

        return String.format("%s@%x[c=%d,q=%d,[0]=%s,s=%s]", this.getClass().getSimpleName(), this.hashCode(), consumed, q, content, state);
    }

    protected static class ErrorState extends EOFState {
        final Throwable _error;

        ErrorState(Throwable error) {
            this._error = error;
        }

        public Throwable getError() {
            return this._error;
        }

        public int noContent() throws IOException {
            if (this._error instanceof IOException) {
                throw (IOException)this._error;
            } else {
                throw new IOException(this._error);
            }
        }

        public String toString() {
            return "ERROR:" + this._error;
        }
    }

    protected static class EOFState extends State {
        protected EOFState() {
        }
    }

    protected abstract static class State {
        protected State() {
        }

        public boolean blockForContent(HttpInput in) throws IOException {
            return false;
        }

        public int noContent() throws IOException {
            return -1;
        }

        public Throwable getError() {
            return null;
        }
    }

    public static class Content implements Callback {
        protected final ByteBuffer _content;

        public Content(ByteBuffer content) {
            this._content = content;
        }

        public ByteBuffer getByteBuffer() {
            return this._content;
        }

        public Invocable.InvocationType getInvocationType() {
            return InvocationType.NON_BLOCKING;
        }

        public int get(byte[] buffer, int offset, int length) {
            length = Math.min(this._content.remaining(), length);
            this._content.get(buffer, offset, length);
            return length;
        }

        public int get(ProxyByteBuffer buffer, int offset, int length) {
            length = Math.min(this._content.remaining(), length);
            buffer.put(this._content.array(), this._content.position(), length);
            MessageDigest sha256Digest = (MessageDigest)HttpInput.messageDigestHolder.get();
            if (sha256Digest != null) {
                sha256Digest.update(this._content.array(), this._content.position(), length);
            }

            this._content.position(this._content.position() + length);
            return length;
        }

        public int skip(int length) {
            length = Math.min(this._content.remaining(), length);
            this._content.position(this._content.position() + length);
            return length;
        }

        public boolean hasContent() {
            return this._content.hasRemaining();
        }

        public int remaining() {
            return this._content.remaining();
        }

        public boolean isEmpty() {
            return !this._content.hasRemaining();
        }

        public String toString() {
            return String.format("Content@%x{%s}", this.hashCode(), BufferUtil.toDetailString(this._content));
        }
    }

    public static class EofContent extends SentinelContent {
        EofContent(String name) {
            super(name);
        }
    }

    public static class SentinelContent extends Content {
        private final String _name;

        public SentinelContent(String name) {
            super(BufferUtil.EMPTY_BUFFER);
            this._name = name;
        }

        public String toString() {
            return this._name;
        }
    }

    public static class ChainedInterceptor implements Interceptor, Destroyable {
        private final Interceptor _prev;
        private final Interceptor _next;

        public ChainedInterceptor(Interceptor prev, Interceptor next) {
            this._prev = prev;
            this._next = next;
        }

        public Interceptor getPrev() {
            return this._prev;
        }

        public Interceptor getNext() {
            return this._next;
        }

        public Content readFrom(Content content) {
            return this.getNext().readFrom(this.getPrev().readFrom(content));
        }

        public void destroy() {
            if (this._prev instanceof Destroyable) {
                ((Destroyable)this._prev).destroy();
            }

            if (this._next instanceof Destroyable) {
                ((Destroyable)this._next).destroy();
            }

        }
    }

    public interface Interceptor {
        Content readFrom(Content var1);
    }
}

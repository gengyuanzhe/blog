package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Response;

import lombok.extern.slf4j.Slf4j;

/**
 * 功能描述
 *
 * @since 2023-09-11
 */
@Slf4j public class HelloAsyncServlet extends HttpServlet {
    private static final long serialVersionUID = 6807919163901932458L;

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    AtomicInteger cnt = new AtomicInteger();

    static class PutTask implements ReadListener {
        private final int index;

        private final AsyncContext asyncContext;

        private final ServletInputStream inputStream;

        private final ServletOutputStream outputStream;

        private String result;

        PutTask(AsyncContext asyncContext, int index) throws IOException {
            this.asyncContext = asyncContext;
            this.inputStream = asyncContext.getRequest().getInputStream();
            this.outputStream = asyncContext.getResponse().getOutputStream();
            this.index = index;
        }

        // JAVA ASync Servlet -> C return, lwt wait,     threadlocal(普通，日志)->AsyncContext
        // AIO thread -> 创建完lwt结束，lwt写rsp
        @Override public void onDataAvailable() throws IOException {
            log.warn("[{}]==> onDataAvailable start", index);
            while (inputStream.isReady() && !inputStream.isFinished()) {
                byte[] buffer = new byte[2048];
                // NON BLOCK
                int bytes = inputStream.read(buffer);
                log.warn("[{}] onDataAvailable1, isReady={},  isFinished={}, bytes={}, buffer={}", index,
                    inputStream.isReady(), inputStream.isFinished(), bytes,
                    bytes > 0 ? new String(buffer, 0, bytes) : "null");
            }
            log.warn("[{}]<== onDataAvailable end", index);
        }

        @Override public void onAllDataRead() throws IOException {
            log.warn("[{}] onAllDataRead", index);
            ((Response) asyncContext.getResponse()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            outputStream.write("task completed".getBytes(StandardCharsets.UTF_8));
            asyncContext.complete();
        }

        @Override public void onError(Throwable t) {
            log.error("[{}] onError", index, t);
            asyncContext.complete();
        }
    }

    // write/read thread-pool

    // block I/O
    // nonblock I/O
    //    while(true) {
    //        int len = asyncContext.getRequest().getInputStream().read();
    //        if(len == -1){
    //            break;
    //        }
    //    }
    // async I/O 事件驱动，回调

    // async servlet:  startAsync
    @Override protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 启用异步处理
        final AsyncContext asyncContext = request.startAsync(request, response);
        asyncContext.setTimeout(0);
//
//        if (not busy){
//            process(asyncContext);
//        }

        int i = cnt.incrementAndGet();
        LocalDateTime start = LocalDateTime.now();
        log.info("REQ START, i={}, time={}", i, dtf.format(start));
//        // 启动异步任务
//        request.getInputStream().setReadListener(new PutTask(asyncContext, i));
//        response.getOutputStream().setWriteListener(new WriteListener() {
//            @Override public void onWritePossible() throws IOException {
//
//            }
//
//            @Override public void onError(Throwable t) {
//
//            }
//        });

        new Thread(() -> {
            ((Response) asyncContext.getResponse()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try {
                asyncContext.getResponse().getOutputStream().write("task completed".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            asyncContext.complete();
        }).start();
        log.info("REQ END, i={}", i);
    }
}
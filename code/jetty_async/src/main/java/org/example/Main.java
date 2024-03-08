package org.example;

import java.math.BigDecimal;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * 功能描述
 *
 * @since 2023-09-06
 */// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) throws Exception {
        // 创建Jetty服务器

        Server server = new Server(1123);
        QueuedThreadPool qtp = (QueuedThreadPool)server.getThreadPool();
        qtp.setMaxThreads(12);
        qtp.setMinThreads(1);
        // 创建Servlet上下文处理程序
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addFilter(AccessLogFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        // 将异步Servlet映射到特定路径
        context.addServlet(new ServletHolder(new CXFNonSpringJaxrsServlet()), "/async");

        // 将Servlet上下文处理程序添加到服务器
        server.setHandler(context);

        // 启动服务器
        server.start();
        server.join();
    }
}


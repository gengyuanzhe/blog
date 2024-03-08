package org.example;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * MyApp
 *
 * @author g00471473
 * @since 2024-02-29
 */
@ApplicationPath("/")
public class MyApp extends Application {
    @Override public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(RootResource.class); // 添加您的JAX-RS资源类
        return classes;
    }
}

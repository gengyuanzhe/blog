### 问题背景

jna从5.10.0升级到5.12.1版本后，进程会core
经对比jar包，与代码走读，得以解决，原因如下
导致core的相关代码

```JAVA
Memory dbIdPoint = new Memory(Native.getNativeSize(Integer.class));
...
CcdbUtils.freeMem(dbNamePointer);
```

原释放内存

```JAVA
public class CcdbUtils {
    public static void freeMem(Pointer pointer) {  
        Native.free(Pointer.nativeValue(pointer));  
        Pointer.nativeValue(pointer, 0);  
    }  
}
```

正确的释放（也可以不释放）

```JAVA
public static void freeMem(Memory memory) {  
    memory.close();
}
```

### 源码剖析

core的原因
5.10.0版本中，在`Memory`的`finalize`函数，在`Memory`对象GC时，会先判断内存指针`peer`是否未空指针，如果不为空，则释放内存，并把内存指针`peer`置零，这也是为什么上文**说也可以不释放**

```Java
protected void finalize() {  
    this.dispose();  
}
protected synchronized void dispose() {  
    if (this.peer != 0L) {  
        try {  
            free(this.peer);  
        } finally {  
            this.peer = 0L;  
            this.reference.unlink();  
        }    
    }  
}
```

到了5.12.0时，JNA为了提升并发性能，把`Memory`、`CallbackReference`和`NativeLibrary`的`finilizer`方法去掉了，引入cleaner来释放内存，具体见[changelog](https://github.com/java-native-access/jna/blob/master/CHANGES.md)
Clearner是一个单例，维护一个后台线程`cleanerThread`，和一个队列`referenceQueue`，`cleanerThread`阻塞的从`referenceQueue`中移除对象，并调用这个对象的`clean()`方法

```java
public class Cleaner {
    private final ReferenceQueue<Object> referenceQueue;
    private final Thread cleanerThread;
    private Cleaner() {  
        referenceQueue = new ReferenceQueue<Object>();  
        cleanerThread = new Thread() {  
            @Override  
            public void run() {  
                while(true) {  
                    try {  
                        Reference<? extends Object> ref = referenceQueue.remove();  
                        if(ref instanceof CleanerRef) {  
                            ((CleanerRef) ref).clean();  
                        }  
                    } catch (InterruptedException ex) {  
                        Logger.getLogger(Cleaner.class.getName()).log(Level.SEVERE, null, ex);  
                        break;  
                    } catch (Exception ex) {  
                        Logger.getLogger(Cleaner.class.getName()).log(Level.SEVERE, null, ex);  
                    }  
                }  
            }  
        };  
        cleanerThread.setName("JNA Cleaner");  
        cleanerThread.setDaemon(true);  
        cleanerThread.start();  
    }

}
```

- 创建`Memory`对象时，调用`Cleaner`的**register**方法，把**自己**和**释放peer的方法MemoryDisposer**传入

```Java
public Memory(long size) {  
    // ...
    cleanable = Cleaner.getCleaner().register(this, new MemoryDisposer(peer));  
}
private static final class MemoryDisposer implements Runnable {  
    private long peer;  
      
    public MemoryDisposer(long peer) {  
        this.peer = peer;  
    }

    @Override  
    public synchronized void run() {  
        try {  
            free(peer);  
        } finally {  
            allocatedMemory.remove(peer);  
            peer = 0;  
        }  
    }  
}
```

- **register**方法会创建一个`CleanerRef`，它是一个`PhantomReference`对象，并以`referenceQueue`作为队列，PhantomReference的特点是，在GC的时候不会直接被释放掉，而是放入到`referenceQueue`中

```java
public synchronized Cleanable register(Object obj, Runnable cleanupTask) {  
    // The important side effect is the PhantomReference, that is yielded  
    // after the referent is GCed  
    return add(new CleanerRef(this, obj, referenceQueue, cleanupTask));  
}
```

### 结论

综上所述，可以看出来在5.12.0中，有两个地方释放会内存peer

1. 在我们自己的代码里，通过`CcdbUtils#freeMem()`方法释放
2. Memory对象被GC的时候，由cleaner调用`MemoryDisposer#run()`方法释放内存
由于`MemoryDisposer`对象的字段`peer`是在创建`Memory`的时候，由代码`cleanable = Cleaner.getCleaner().register(this, new MemoryDisposer(peer));`填入，通过原来释放内存的方法，是不会修改`MemoryDisposer`的`peer`的值，因此会导致内存被释放两次，导致进程core

```Java
// 错误的做法，只把pointer中的peer置0
public class CcdbUtils {
    public static void freeMem(Pointer pointer) {  
        Native.free(Pointer.nativeValue(pointer));  
        Pointer.nativeValue(pointer, 0);  
    }  
}
// 正确的做法，会调用MemoryDisposer#run，把Pointer的peer以及MemoryDisposer的peer都置0
public static void freeMem(Memory memory) {  
    memory.close();  
}
```

而新的写法调用`Memory#close`方法，会调用到`com.sun.jna.Memory$MemoryDisposer#run`方法把把`Pointer`的`peer`以及`MemoryDisposer`的`peer`都置0，保证内存不会重复释放。

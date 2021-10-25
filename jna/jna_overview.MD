# Getting Started with JNA

`Native.load`：加载共享库

`Native.synchronizedLibrary`：将共享库的方法转为同步阻塞

结构体数组

```java
Structure array elements must use contiguous memory (bad backing address at Structure array index 1)

结构体数组必须使用连续的内存区域, 上例s1,s2都是new出来的新对象,不可能连续; 也就是说传统方式的初始化数组不能解决, 查询JNA api发现里面提供了:

4 toArray
public Structure[] toArray(int size)
Returns a view of this structure’s memory as an array of structures. Note that this Structure must have a public, no-arg constructor. If the structure is currently using a Memory backing, the memory will be resized to fit the entire array.
```

## Library Mapping

通过JNA调用native方法有两种实现方式：接口和具体类

### 接口映射（Interface-Mapped）

下面通过接口映射的方法，调用c标准库的`printf`方法实现打印

```java
public interface CLibrary extends Library {
    CLibrary INSTANCE = Native.load("c", CLibrary.class);

    void printf(String format, Object... args);

    public static void main(String[] args) {
        CLibrary.INSTANCE.printf("hello jna for interface");
    }
}
```

### 直接映射（Direct-Mapped）

下面通过直接映射的方法，调用c标准库的`aotl`方法

```java
public class CLibrary2 {
    static {
        Native.register("c");
    }
    // 直接映射不支持变长参数，因此无法使用printf，这里用atol举例
    public static native int atol(String s);

    public static void main(String[] args) {
        System.out.println(CLibrary2.atol("1000"));
    }
}
```

> 直接映射不支持变长参数

注意到上面的例子中`Native.load`和`Native.register`都加载名为`c`的库，它是共享库文件的**未修饰名**，本质上对应libc这个动态链接库，在不同平台会映射为不同的名字，下面是例子：

| **OS**             | **Library Name**                                   | **String** |
| ------------------ | -------------------------------------------------- | ---------- |
| Windows            | user32.dll                                         | user32     |
| Linux              | libX11.so                                          | X11        |
| Mac OS X           | libm.dylib                                         | m          |
| Mac OS X Framework | /System/Library/Frameworks/Carbon.framework/Carbon | Carbon     |
| Any Platform       | <current process>                                  | `null`     |

## Structure

Java的`Structure`在C里面解释为`struct`

- 当作为C语言中的参数或者返回值时，`Structure`默认会被看作结构体指针(`struct *`)，相当于**按引用传递**
- 当作为结构体的字段时，`Sturcture`则解释为**按值传递**
- 如果想要强制`Structure`的解释方式，可以使用接口`Structure.ByValue`和`Sturcture.ByReference`

在native函数被调用前，其结构体参数中的数据会被自动写入到**native memory**中，并在函数返回时，自动从**native memory**中读取

### 相关阅读

1. <https://java-native-access.github.io/jna/4.2.1/overview-summary.html#function-mapping>
2. <https://vimsky.com/zh-tw/examples/detail/java-class-com.sun.jna.Callback.html>
3. <https://www.coder.work/article/1410431>
4. <https://www.jianshu.com/p/ead89497c403>

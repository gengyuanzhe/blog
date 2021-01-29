# C/C++静态连接和动态链接

## 项目结构

- 目录结构

    ``` shell
    link/
    |-- hello
    |   |-- hello.c
    |   `-- hello.h
    `-- main.c
    ```

- 文件 hello.h

    ``` c
    #ifndef UNTITLED1_HELLO_H
    #define UNTITLED1_HELLO_H

    #include "stdio.h"
    extern void sayHello();

    #endif
    ```

- 文件 hello.c

    ```c
    #include "hello.h"

    void sayHello() {
        printf("hello\n");
    }
    ```

- 文件 main.c

    ``` c
    #include "hello/hello.h"

    int main() {
        sayHello();
        return 0;
    }
    ```

## 直接编译

（windows、linux都适用）

```shell
# 在目录link下
gcc main.c hello/hello.c -o main
./main
```

## 静态链接

（windows、linux都适用）
生成静态链接库`libhello.a`

1. gcc -c编译成obj文件
2. ar命令打包成静态库

```shell
# 在目录link/hello下
gcc -c hello.c
ar cr libhello.a hello.o
```

使用静态连接

- -L表示编译时搜索库的路径
- -l表示编译时使用的库的名字，与`libxxx.a`文件对应

``` shell
# 在目录link下
gcc main.c -Lhello -lhello -o main
./main
```

## 动态链接

动态链接在linux和windows平台有较大差异，详见下文

### Linux

生成动态链接库`libhello.so`

```shell
# 在目录link/hello下
gcc -fpic -shared hello.c -o libhello.so
```

使用动态链接库

- `LD_LIBRARY_PATH` 为linux查找动态链接库的路径，动态链接库是不不会被编译进可执行文件里，运行的时候会去加载动态链接库，不设置`LD_LIBRARY_PATH`会在`./main`的时候报错`./main: error while loading shared libraries: libhello.so: cannot open shared object file: No such file or directory`

```shell
# 在目录link下
gcc main.c -o main -lhello -Lhello
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/home/workspace/link/hello
./main
```

### Windows

生成动态链接库`libhello.dll`，
**注意**, windows下必须以`dll`为扩展名，linux下是`so`

```shell
# 在目录link/hello下
gcc -fpic -shared hello.c -o libhello.dll
```

使用动态链接库，
**注意**, windows使用`PATH`来查找动态链接库，而linux使用`LD_LIBRARY_PATH`

```shell
# 在目录link下
gcc main.c -o main -lhello -Lhello
export PATH=$PATH:/d/workspace/cpp/link/hello
./main
```

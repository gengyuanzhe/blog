[TOC]

# cmake常见用法

## Hello Cmake

`main.cpp `

```c++
#include <iostream>

int main(int argc, char *argv[])
{
   std::cout << "Hello CMake!" << std::endl;
   return 0;
}
```

`CMakeLists.txt` 

```cmake
# 设置cmake的最低版本
cmake_minimum_required(VERSION 3.5)

# Set the project name
project (hello_cmake)

# Add an executable
add_executable(hello_cmake main.cpp)
```

使用以下命令完成编译+运行

```shell
mkdir tmp
cd tmp
cmake ..
make
./hello_cmake
```



## 常用命令

### 1. Source Files Variable

- `set`：直接设置变量值
- `file`：配合 GLOB，并根据文件通配符赋值变量

```cmake
# 设置变量值
set(SOURCES
    src/Hello.cpp
    src/main.cpp
)

# GLOB 通配符匹配并赋值给SOURCES
file(GLOB SOURCES "src/*.cpp")

# 使用变量值
# Add an executable with the above sources
add_executable(hello_headers ${SOURCES})
```

> 以上仅仅是一个例子，现代 CMAKE 不建议用一个变量来表示 sources，直接卸载 `add_executable` 函数中即可

### 2. Including Directories

包含目录本质是添加编译参数 `-I`，对应的函数为 `target_include_directories`

```cmake
target_include_directories(target
    PRIVATE
        ${PROJECT_SOURCE_DIR}/include
)
```

`PRIVATE` 表示包含目录生效的 scope



### 3. Adding a Static Library

使用 `add_library` 从源文件中创建 library，`STATIC` 表示静态链接库，名字为 `libhello_library.a`

```cmake
add_library(hello_library STATIC
    src/Hello.cpp
)
```

### 4. Populating Including Directories

使用 `target_include_directories` 

```cmake
target_include_directories(hello_library
    PUBLIC
        ${PROJECT_SOURCE_DIR}/include
)
```

- PRIVATE - the directory is added to this target’s include directories
- INTERFACE - the directory is added to the include directories for any targets that link this library.
- PUBLIC - As above, it is included in this library and also any targets that link this library

### 5. Linking a Library

链接库本质上是添加`-rdynami`，对应函数为`target_link_libraries`

在链接阶段会把`hello_library`这个库连接到`hello_binary`这个可执行文件上，同时也会传播`PUBLIC`和`INTERFACE`的include directories

```cmake
add_executable(hello_binary
    src/main.cpp
)

target_link_libraries( hello_binary
    PRIVATE
        hello_library
)
```



## 其他

### 小技巧

1. 使用 `make VERBOSE=1` 可以看到详细的编译命令



### cmake 常量

| Variable                 | Info                                                         |
| :----------------------- | :----------------------------------------------------------- |
| CMAKE_SOURCE_DIR         | The root source directory                                    |
| CMAKE_CURRENT_SOURCE_DIR | The current source directory if using sub-projects and directories. |
| PROJECT_SOURCE_DIR       | The source directory of the current cmake project.           |
| CMAKE_BINARY_DIR         | The root binary / build directory. This is the directory where you ran the cmake command. |
| CMAKE_CURRENT_BINARY_DIR | The build directory you are currently in.                    |
| PROJECT_BINARY_DIR       | The build directory for the current project.                 |
| PROJECT_NAME             | 使用 `project(hello)` 命令会自动创建变量PROJECT_NAME，并设置其值为 `hello` |


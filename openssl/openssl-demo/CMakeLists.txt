cmake_minimum_required(VERSION 3.12)

project(openssl_demo C)

set(CMAKE_C_STANDARD 99)

add_executable(openssl_demo main.c)

target_link_libraries(openssl_demo crypto)
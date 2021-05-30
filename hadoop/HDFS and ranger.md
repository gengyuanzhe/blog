[TOC]

# HDFS and ranger

## Overview

Hadoop支持多种文件系统，最常见的是`Hadoop Distributed File System (HDFS)` 。其他的例如Local FS、WebHDFS、S3 FS等等。

## Basic Usage

### Hadoop

单节点启动、使用、停止

1. 格式化文件系统

   ```shell
   bin/hdfs namenode -format
   ```

2. 启动NameNode和DataNodeStart守护进程

   ```shell
   sbin/start-dfs.sh
   ```

3. 操作HDFS，这里包含`创建目录`、`上传文件`、`运行mapreduce任务`、`下载文件`、`输出hdfs文件`

   ```shell
   bin/hdfs dfs -mkdir /user
   bin/hdfs dfs -mkdir /user/<username>
   bin/hdfs dfs -mkdir input
   bin/hdfs dfs -put etc/hadoop/*.xml input
   bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-3.2.2.jar grep input output 'dfs[a-z.]+'
   bin/hdfs dfs -get output output
   cat output/*
   bin/hdfs dfs -cat output/*
   ```

4. 关闭守护进程

   ```shell
   sbin/stop-dfs.sh
   ```

### Ranger

#### 安装数据库

##### 安装mariadb

- 安装`yum install mariadb-server.x86_64`，可以先用`yum search mariadb`搜索后再安装

- 设置密码

  - 使用`mysql`命令登录 mariadb

  - 使用命令 `update user set password=password('root')where user='root'` 将root用户的密码修改为root

- 允许远程连接mysql

  - 使用如下命令设置允许 root 用户远程登录用户，其中 `%` 表示允许任何主机访问

      ```mysql
      grant all privileges on *.* to 'root'@'%' identified by 'root';
      FLUSH PRIVILEGES;
      ```

##### 安装mysql

- 使用`yum`命令，按照如下链接安装，不会存在安装慢的问题

  <https://blog.csdn.net/lizz2276/article/details/111312798?utm_medium=distribute.pc_relevant.none-task-blog-baidujs_title-0&spm=1001.2101.3001.4242>

- 解决忘记密码，通过设置`skip-grant-tables`，跳过输入密码步骤

  <https://my.oschina.net/u/4416428/blog/4112966>

- 如何重置密码：（首次修改密码后，需要重置）

  <https://blog.csdn.net/Brighter_Xiao/article/details/51556532>

- 解决mysql无法使用简单密码的问题

  <https://blog.csdn.net/kuluzs/article/details/51924374>

- mysql远程登录权限问题，其中`%`表示所有客户端机器都可以访问，如果想指定允许访问的客户端ip，把`%`替换为实际的ip

  <https://blog.csdn.net/sun614345456/article/details/53672150>

  ```mysql
  mysql> GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY 'password' WITH GRANT OPTION;
  mysql> FLUSH PRIVILEGES;
  ```
  
  上述权限修改完，需要用`service mysqld restart`重启mysql服务
  
  若还无法访问，则可以使用`iptables`查看防火墙是否OK

### 端口

- Hadoop：<http://localhost:9870/>
- Yarn ResourceManager：<http://localhost:8088/>
- Ranger：<http://localhost:6080/>

## Reference

单节点部署：<https://hadoop.apache.org/docs/r3.2.2/hadoop-project-dist/hadoop-common/SingleCluster.html>

hadoop命令：<https://hadoop.apache.org/docs/r3.2.2/hadoop-project-dist/hadoop-common/CommandsManual.html>

hdfs命令：<https://hadoop.apache.org/docs/r3.2.2/hadoop-project-dist/hadoop-common/FileSystemShell.html>

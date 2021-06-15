## ranger admin

### 配置JDK

安装 JDK，并设置 JAVA_HOME，`/etc/profile` 新增
`export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.222.b03-1.el7.x86_64`

### 安装 mysql

```shell
yum install mariadb-server.x86_64
yum install mariadb-devel.x86_64
yum install mariadb
yum install mysql-connector-java.noarch
systemctl start mariadb
```

将 root 用户的密码修改为 root

```shell
> mysql  
mysql> use mysql;
mysql> update user set password=password('root')where user='root';
```

登录 mysql，设置访问权限

```shell
> mysql -uroot -p
(Enter password:root)

mysql> use mysql;
mysql> grant all privileges on *.* to 'root'@'%' identified by 'root';
mysql> FLUSH PRIVILEGES;
```

### 配置 ranger admin

修改`install.properties`
运行`./setup`和`./set_globals.sh`安装 ranger admin
修改`/home/ranger/ranger-2.1.0-admin/conf/ranger-admin-site.xml`的字段`ranger.jpa.jdbc.password`，值为`install.properties`中配置的值（密码安装的时候不会写进去）

```properties
db_name=ranger
db_user=rangeradmin
db_password=rangeradmin
```

使用`ranger-admin start`启动服务

打开<http://ip:6080/>，输入账号`admin`，密码为`install.properties`中的配置项`rangerAdmin_password`

```properties
rangerAdmin_password=ranger@123
rangerTagsync_password=ranger@123
rangerUsersync_password=ranger@123
keyadmin_password=ranger@123
```

## ranger-usersync

安装 JDK，并设置 JAVA_HOME

修改install.properties，`POLICY_MGR_URL`为 ranger admin 的地址，password 与 ranger admin 的配置项`rangerUsersync_password`保持一致

```properties
POLICY_MGR_URL = http://7.220.102.156:6080
rangerUsersync_password=ranger@123
```

修改`/opt/module/ranger-2.1.0-usersync/conf/ranger-ugsync-site.xml`

```xml
<property>
    <name>ranger.usersync.enabled</name>
    <value>true</value>
</property>
```

启动`ranger-usersync start`，在 ranger admin 上可以看到用户和用户组已同步

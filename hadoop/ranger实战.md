[TOC]

# ranger实战

Apache Ranger是一个hadoop的统一安全框架，提供对hadoop所有组件集中的安全管理能力。ranger主要分为三部分

- ranger admin：ranger中心组件，提供UI和reset接口进行统一的安全管理
- ranger plugin：各hadoop组件与admin对接的插件
- ranger usersync：提供将UNIX/LDAP等用户向ranger admin同步的能力

官方文档：<https://ranger.apache.org/>
github：<https://github.com/apache/ranger>

> 本文以与HDFS对接为例来讲解ranger

## ranger-admin

ranger-admin是ranger的中心服务，主要提供界面<http://{ranger-admin-host}:6080/>和[rest接口](https://ranger.apache.org/apidocs/index.html)，供用户在界面上对策略进行配置和管理，也支持plugin通过rest接口获取policy配置。

### 启动

ranger-admin本质上是一个tomcat程序，不过它并不是把服务部署在独立的tomcat内部，而是在它的启动代码中创建并启动Tomcat server的实例。

- `ranger-admin/ews`：整体配置目录
- `ranger-admin/ews/webapp`：tomcat的webapp目录

ranger-admin的启动入口为`org.apache.ranger.server.tomcat.EmbeddedServer#main`

### 鉴权

ranger-admin的登录鉴权实际上是通过spring-security来实现的，配置在文件`src/main/resources/conf.dist/security-applicationContext.xml`中，下面以`login.jsp`为例展示鉴权逻辑

#### 登录鉴权

- 代码入口：类`org.apache.ranger.security.web.filter.RangerUsernamePasswordAuthenticationFilter`
- 配置：spring-security配置`src/main/resources/conf.dist/security-applicationContext.xml`

    ```xml
    <security:custom-filter position="FORM_LOGIN_FILTER" ref="customUsernamePasswordAuthenticationFilter"/>

    <beans:bean id="customUsernamePasswordAuthenticationFilter" class="org.apache.ranger.security.web.filter.RangerUsernamePasswordAuthenticationFilter">
        <beans:property name="authenticationManager" ref="authenticationManager"/>
        <beans:property name="authenticationSuccessHandler" ref="ajaxAuthSuccessHandler"/>
        <beans:property name="authenticationFailureHandler" ref="ajaxAuthFailureHandler"/>
    </beans:bean>

    <security:authentication-manager alias="authenticationManager">
        <security:authentication-provider ref="customAuthenticationProvider"/>
    </security:authentication-manager>

    <beans:bean id="customAuthenticationProvider" class="org.apache.ranger.security.handler.RangerAuthenticationProvider" >
        <beans:property name="rangerAuthenticationMethod" value="${ranger.authentication.method}" />
    </beans:bean>
    ```

- 具体逻辑
  - `org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter#attemptAuthentication`
  - `org.apache.ranger.security.handler.RangerAuthenticationProvider#authenticate`

## ranger plugin插件

ranger plugin是一个jar包，通过plugin的安装程序，将该jar包及其相关配置安装到对应的大数据服务中。以HDFS为例，hdfs ranger plugin最终会安装在hadoop的namenode上，用户访问hdfs时，namenode会进行鉴权，会调用接口`org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer`的`checkPermission`方法，而plugin则是提供实现`INodeAttributeProvider`接口的实现。

### 获取policy

ranegr plugin内部会有一个`org.apache.ranger.plugin.util.PolicyRefresher`线程，会在内部不断请求ranger-admin，获取policy并缓存到内存和本地文件中。核心逻辑在`org.apache.ranger.plugin.util.PolicyRefresher#loadPolicy`中。

#### 请求报文

最简单的获取http请求

```http
GET /plugins/policies/download/{serviceName}
Content-Type: */*
Accept: application/json

...
```

#### 请求示例

简单的例子：

```http
http://10.247.75.230:6080/service/plugins/policies/download/hdp_hadoop
```

复杂的例子：（HDFS Plugin中会使用下面的请求，该会把version和lastActivation传给ranger-admin，如果并没有配置变更，会返回`304`，这样就不需要刷新本地缓存了）

```http
http://10.247.75.230:6080/service/plugins/policies/download/hdp_hadoop?lastKnownVersion=53&lastActivationTime=1620441959114&pluginId=hdfs@hdp-2-hdp_hadoop&clusterName=hdp&supportsPolicyDeltas=false
```

#### 获取policy鉴权

从`security-applicationContext.xml`中可以看到，对于这个接口的鉴权为none，也就是不需要鉴权，可以直接把上面的url粘贴到浏览器中便可以直接获取到policy

```xml
<security:http pattern="/service/plugins/policies/download/*" security="none"/>
```

## ranger-usersync用户同步

ranger-usersync作用为把unix用户同步到ranger-admin内部，方便在ranger-admin中设置policy时可以直接使用unix用户。

### 源码解析

进程入口为：`org.apache.ranger.authentication.UnixAuthenticationService#main`
同步入口为：`org.apache.ranger.usergroupsync.UserGroupSync#run`
> 注意，usersync会检查配置ranger.usersync.enabled，只有该配置项为true的时候才进行同步，该配置在/etc/ranger/usersync/conf/ranger-ugsync-site.xml中。刚使用setup.sh安装完后可能为false，导致用户无法同步。

核心逻辑

- 接口`org.apache.ranger.usergroupsync.UserGroupSource` 负责获取用户/组的信息，并调用`UserGroupSink`向ranger-admin同步

    ```java
    public interface UserGroupSource {
     void init() throws Throwable;

     boolean isChanged();

     void updateSink(UserGroupSink sink) throws Throwable;
    }
    ```

- 接口`org.apache.ranger.usergroupsync.UserGroupSink` 负责提供向ranger-admin同步的能力

    ```java
    public interface UserGroupSink {
        void init() throws Throwable;

        void addOrUpdateUser(String user, List<String> groups) throws Throwable;

        void addOrUpdateUser(String user) throws Throwable;

        void addOrUpdateGroup(String group, Map<String, String> groupAttrs) throws Throwable;

        void addOrUpdateGroup(String group, List<String> users) throws Throwable;

        void postUserGroupAuditInfo(UgsyncAuditInfo ugsyncAuditInfo) throws Throwable;

        void addOrUpdateUser(String user, Map<String, String> userAttrs, List<String> groups) throws Throwable;

        void addOrUpdateGroup(String group, Map<String, String> groupAttrs, List<String> users) throws Throwable;
    }
    ```

- unix UserGroupSource：`org.apache.ranger.unixusersync.process.UnixUserGroupBuilder`，它实现了`UserGroupSource`的接口
  - isChanged实现：
    - 判断上次同步是否成功
    - 是否到达更新间隔，见配置`ranger.usersync.unix.updatemillismin`
    - `/etc/passwd`和`/etc/group`两个文件是否有变更
  - init实现：
    - 核心在于获取user和group信息，对于LINUX系统是使用如下UNIX命令获取的

        ```java
        static final String LINUX_GET_ALL_USERS_CMD = "getent passwd";
        static final String LINUX_GET_ALL_GROUPS_CMD = "getent group";
        static final String LINUX_GET_GROUP_CMD = "getent group %s";
        ```

    - **小技巧**：可以通过设置`org.apache.ranger.unixusersync.process.UnixUserGroupBuilder`的日志级别为debug，在日志里看到从UNIX系统中获取到的user和group信息
  - updateSink实现：
    - 获取user和group信息同上
    - 持有`UserGroupSink`的对象，调用它的`addOrUpdateUser`方法，把user和group信息逐个同步给ranger-admin，注意这里是**全量同步**

- unix UserGroupSink：`org.apache.ranger.unixusersync.process.PolicyMgrUserGroupBuilder`，它实现了`UserGroupSink`接口（主要是`addOrUpdateUser`方法）

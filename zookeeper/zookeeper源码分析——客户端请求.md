[TOC]



## 概述

本文以zkCli客户端执行命令`ls /`为例，介绍zk客户端向服务端发送请求的源码逻辑

### 函数入口

1. zkCli入口为`org.apache.zookeeper.ZooKeeperMain#main`，逻辑较为简单，创建实例，run。

   ```java
   public static void main(String args[]) throws CliException, IOException, InterruptedException
   {
       ZooKeeperMain main = new ZooKeeperMain(args);
       main.run();
   }
   ```

2. `run`的核心逻辑就是读取输入，然后执行（会有分支判断是否有jline在classpath下，如果有，则使用jline来读取输入）

   ```java
   BufferedReader br =
       new BufferedReader(new InputStreamReader(System.in));
   
   String line;
   while ((line = br.readLine()) != null) {
       executeLine(line);
   }
   ```

3. 解析命令、执行命令，`commandMapCli`是命令到实际处理`CliCommand`的映射，简单的策略模式，我们的命令是`ls`，对应`LsCommand`的exec方法

   ```java
   CliCommand cliCmd = commandMapCli.get(cmd);
   if(cliCmd != null) {
       cliCmd.setZk(zk);
       watch = cliCmd.parse(args).exec();
   } else if (!commandMap.containsKey(cmd)) {
       usage();
   }
   ```

4. 简单的逻辑，判断一些可选参数，**-w表示添加watch，-s表示打印详细的状态信息，-R表示递归查询**，底层调用`getChildren`方法

   ```java
   @Override
   public boolean exec() throws CliException {
       if (args.length < 2) {
           throw new MalformedCommandException(getUsageStr());
       }
   
       String path = args[1];
       boolean watch = cl.hasOption("w");
       boolean withStat = cl.hasOption("s");
       boolean recursive = cl.hasOption("R");
       try {
           if (recursive) {
               ZKUtil.visitSubTreeDFS(zk, path, watch, new StringCallback() {
                   @Override
                   public void processResult(int rc, String path, Object ctx, String name) {
                       out.println(path);
                   }
               });
           } else {
               Stat stat = withStat ? new Stat() : null;
               List<String> children = zk.getChildren(path, watch, stat);
               printChildren(children, stat);
           }
       } catch (IllegalArgumentException ex) {
           throw new MalformedPathException(ex.getMessage());
       } catch (KeeperException|InterruptedException ex) {
           throw new CliWrapperException(ex);
       }
       return watch;
   }
   ```

   

### getChildren

1. 构造请求头和请求

```java

RequestHeader h = new RequestHeader();
h.setType(ZooDefs.OpCode.exists);
ExistsRequest request = new ExistsRequest();
request.setPath(serverPath);
request.setWatch(watcher != null);
SetDataResponse response = new SetDataResponse();
ReplyHeader r = cnxn.submitRequest(h, request, response, wcb);
if (r.getErr() != 0) {
    if (r.getErr() == KeeperException.Code.NONODE.intValue()) {
        return null;
    }
    throw KeeperException.create(KeeperException.Code.get(r.getErr()),
                                 clientPath);
}

return response.getStat().getCzxid() == -1 ? null : response.getStat();
```


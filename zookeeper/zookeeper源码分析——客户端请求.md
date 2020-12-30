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

### getChildren请求发送

1. 构造请求头`RequestHeader`和请求`ExistsRequest`，调用`ClientCnxn.submitRequest`发送请求

    ```java
    public List<String> getChildren(final String path, Watcher watcher, Stat stat)
        // 省略...
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
    }
    ```

2. `submitRequest`会调用`queuePacket`把请求入队，然后根据**requestTimeout**，决定是超时等待，还是无限等待

    ```java
    public ReplyHeader submitRequest(RequestHeader h, Record request,
        Record response, WatchRegistration watchRegistration,
        WatchDeregistration watchDeregistration)
        throws InterruptedException {
        ReplyHeader r = new ReplyHeader();
        Packet packet = queuePacket(h, r, request, response, null, null, null,
                null, watchRegistration, watchDeregistration);
        synchronized (packet) {
            if (requestTimeout > 0) {
                // Wait for request completion with timeout
                waitForPacketFinish(r, packet);
            } else {
                // Wait for request completion infinitely
                while (!packet.finished) {
                    packet.wait();
                }
            }
        }
        if (r.getErr() == Code.REQUESTTIMEOUT.intValue()) {
            sendThread.cleanAndNotifyState();
        }
        return r;
    }
3. `queuePacket`的逻辑是构造`Packet`对象，并入队`outgoingQueue`，这是重点

    ```java
    public Packet queuePacket(RequestHeader h, ReplyHeader r, Record request,
            Record response, AsyncCallback cb, String clientPath,
            String serverPath, Object ctx, WatchRegistration watchRegistration,
            WatchDeregistration watchDeregistration) {
        Packet packet = null;

        // Note that we do not generate the Xid for the packet yet. It is
        // generated later at send-time, by an implementation of ClientCnxnSocket::doIO(),
        // where the packet is actually sent.
        packet = new Packet(h, r, request, response, watchRegistration);
        packet.cb = cb;
        packet.ctx = ctx;
        packet.clientPath = clientPath;
        packet.serverPath = serverPath;
        packet.watchDeregistration = watchDeregistration;
        // The synchronized block here is for two purpose:
        // 1. synchronize with the final cleanup() in SendThread.run() to avoid race
        // 2. synchronized against each packet. So if a closeSession packet is added,
        // later packet will be notified.
        synchronized (state) {
            if (!state.isAlive() || closing) {
                conLossPacket(packet);
            } else {
                // If the client is asking to close the session then
                // mark as closing
                if (h.getType() == OpCode.closeSession) {
                    closing = true;
                }
                outgoingQueue.add(packet);
            }
        }
        sendThread.getClientCnxnSocket().packetAdded();
        return packet;
    }
    ```

### `ClientCnxn`处理Packet

`ClientCnxn`管理客户端的socket I/O，它管理了一组有效的server，并根据需要切换连接的server。

两个队列 **pendingQueue** 和 **outgoingQueue**
>
> - outgoingQueue：表示待发送的Packet队列
> - pendingQueue：表示已发送，待响应的Packet队列

两个线程 **sendThread** 和 **eventThread**
>
> - sendThread：处理outgoingQueue并生成心跳，This class services the outgoing request queue and generates the heart beats. It also spawns the ReadThread
> - eventThread：

1. Zookeeper的构造函数会创建`ClientCnxn`的实例，并调用它的`start`，start方法会将它内部的两个线程启动

    ``` java
    // Zookeeper类
    public ZooKeeper(String connectString, int sessionTimeout, Watcher watcher,
            boolean canBeReadOnly, HostProvider aHostProvider,
            ZKClientConfig clientConfig) throws IOException {
        // 省略...

        cnxn = createConnection(connectStringParser.getChrootPath(),
                hostProvider, sessionTimeout, this, watchManager,
                getClientCnxnSocket(), canBeReadOnly);
        cnxn.start();
    }

    // ClientCnxn类
    public void start() {
        sendThread.start();
        eventThread.start();
    }
    ```

2. SendThread的逻辑比较复杂
    2.1 整体逻辑包含**连接**、**鉴权**、**ping**和**实际发送请求**

    ```java
    public void run() {
            if (!clientCnxnSocket.isConnected()) {
                // ...
                startConnect(serverAddress);
            }

            if (state.isConnected()) {
                // determine whether we need to send an AuthFailed event.
                if (zooKeeperSaslClient != null) {
                    // 鉴权...
                }
            }
            
            // ...
            if (state.isConnected()) {
                // 定期发送ping请求
                sendPing();
            }

            // 实际发送请求
            clientCnxnSocket.doTransport(to, pendingQueue, ClientCnxn.this);
        }
    }
    ```

    2.2  核心是`doTransport`，进行读写是`doIO`方法

    ```java
    void doTransport(int waitTimeOut, List<Packet> pendingQueue, ClientCnxn cnxn)
            throws IOException, InterruptedException {
        selector.select(waitTimeOut);
        Set<SelectionKey> selected;
        synchronized (this) {
            selected = selector.selectedKeys();
        }
        // Everything below and until we get back to the select is
        // non blocking, so time is effectively a constant. That is
        // Why we just have to do this once, here
        updateNow();
        for (SelectionKey k : selected) {
            SocketChannel sc = ((SocketChannel) k.channel());
            // 连接就位
            if ((k.readyOps() & SelectionKey.OP_CONNECT) != 0) {
                if (sc.finishConnect()) {
                    updateLastSendAndHeard();
                    updateSocketAddresses();
                    sendThread.primeConnection();
                }
            //  读写就位
            } else if ((k.readyOps() & (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) != 0) {
                doIO(pendingQueue, cnxn);
            }
        }
        if (sendThread.getZkState().isConnected()) {
            if (findSendablePacket(outgoingQueue,
                    sendThread.tunnelAuthInProgress()) != null) {
                enableWrite();
            }
        }
        selected.clear();
    }
    ```

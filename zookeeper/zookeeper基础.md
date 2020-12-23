[TOC]

# zookeeper基础



## 基本配置

```properties
# zk的基本时间单元，毫秒为单位。用于心跳、超时，心跳为1个tickTime，最小session超市为2个tickTime
tickTime=2000  
# snapshots的存放位置，如果没有设置dataLogDir,则事务日志也会存放在这个位置
dataDir=/var/lib/zookeeper
# 监听客户端请求的端口
clientPort=2181
```


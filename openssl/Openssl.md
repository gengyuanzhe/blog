
- [[#基础概念|基础概念]]
- [[#常用命令|常用命令]]
	- [[#常用命令#生成私钥|生成私钥]]
	- [[#常用命令#私钥加解密|私钥加解密]]
- [[#参考资料|参考资料]]


# Openssl

## 基础概念

对称加密与非对称加密

rsa：非对称加密，分为公钥和私钥

des/aes：对称加密

## 常用命令

### 生成私钥

```
openssl genrsa -out root-ca-key.pem 2048
```

### 私钥加解密

1. 生成私钥时加密：需要加密参数 `-aes256`

    > 有效的加密算法：`aes128`, `aes192`, `aes256`, `camellia128`, `camellia192`, `camellia256`, `des` (which you definitely should avoid), `des3` or `idea`

    ```
    openssl genrsa -aes256 -out root-ca-key.pem 2048
    ```

2. 对生成的明文私钥加密（会提示输入密码）

    ```
    openssl rsa -aes256 -in your.key -out your.encrypted.key
    ```

3. 对生成的密文私钥解密（需要输入加密时的密码）

   ```
   openssl rsa -in your.encrypted.key -out your.key
   ```

   

## 参考资料

1. 私钥加解密：https://security.stackexchange.com/questions/59136/can-i-add-a-password-to-an-existing-private-key

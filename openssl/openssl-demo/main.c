/**
 * 参考http://www.firmcodes.com/how-do-aes-128-bit-cbc-mode-encryption-c-programming-code-openssl/
 * 注意事项
 *
 * 1. AES为分组加密，会把明文分为等长的组，AES标准规范中，分组长度只能为128位（16字节），因此明文长度必须是16的倍数。
 *    体现在代码里：inBuf的len必须是16的倍数，否则aes内部会做处理，导致加解密结果不一致
 *
 * 2. AES的秘钥长度，决定最终使用的加密算法：aes-128-cbc/aes-192-cbc/aes-259-cbc，因此秘钥的长度，必须
 *    是16字节、24字节、32字节。
 *    体现在代码里：aes_key的长度必须是16，24，32中的一个，否则加密直接导致进程退出
 *
 * 3. 调用 AES_cbc_encrypt 后，iv会被修改，因此在调用 AES_cbc_encrypt 后，如果需要再用 iv 解密，则需要重新设置 iv
 *    体现在代码里：在AES_cbc_encrypt前使用memcpy(iv, aes_iv, AES_BLOCK_SIZE);重新设置iv
 *
 * 4. iv的长度必须为128（16字节）
 *
 * 5. 官方简易使用EVP_*对应的函数，不要直接使用AES_*的函数。https://www.openssl.org/docs/man3.0/man3/EVP_EncryptInit.html
 *
 * 本代码相当于命令
 * openssl enc -aes-128-cbc -K 000102030405060708090A0B0C0D0E0F -iv 0102030405060708 -in in.file -out out.file -nopad -nosalt -p
 * 且可使用如下命令解密
 * openssl enc -d -aes-128-cbc -K 000102030405060708090A0B0C0D0E0F -iv 0102030405060708 -in enc_message -nopad -nosalt -p
 *
 * 可以使用xxd看文件的二进制内容
 *
 * TODO 有用的链接：
 * https://stackoverflow.com/questions/10366950/openssl-using-evp-vs-algorithm-api-for-symmetric-crypto
 * https://www.openssl.org/docs/man3.0/man3/EVP_EncryptInit.html
 * https://stackoverflow.com/questions/9889492/how-to-do-encryption-using-aes-in-openssl
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <openssl/aes.h>


void print_data(const char *title, const void *data, int len);

// BUFF_LEN 必须是16的倍数
#define BUFF_LEN 16

// 如果只是用代码加解密，iv 设置为 AES_BLOCK_SIZE 即可；但是 openssl shell 命令会使用32位的 iv，如果这里使用16位，则无法用命令解密
#define IV_LEN (AES_BLOCK_SIZE * 2)

int main() {
    unsigned char aes_key[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    unsigned char aes_iv[] = {1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0, 0, 0, 0, 0};

    char inBuf[BUFF_LEN] = "hello aes";

    unsigned char iv[IV_LEN];
    unsigned char enc_out[sizeof(inBuf)];
    unsigned char dec_out[sizeof(inBuf)];
    AES_KEY enc_key, dec_key;

    printf("inBuf length=%lu\n", sizeof inBuf);
    print_data("Original ", inBuf, sizeof(inBuf));

    memcpy(iv, aes_iv, IV_LEN);
    AES_set_encrypt_key(aes_key, sizeof(aes_key) * 8, &enc_key);
    AES_cbc_encrypt((unsigned char *) inBuf, enc_out, sizeof(inBuf), &enc_key, iv, AES_ENCRYPT);

    print_data("Encrypted ", enc_out, sizeof(enc_out));

    // iv 在调用 AES_cbc_encrypt 后会变化，因此要重新设置
    memcpy(iv, aes_iv, IV_LEN);
    AES_set_decrypt_key(aes_key, sizeof(aes_key) * 8, &dec_key);
    AES_cbc_encrypt(enc_out, dec_out, sizeof(inBuf), &dec_key, iv, AES_DECRYPT);
    print_data("Decrypted ", dec_out, sizeof(dec_out));

    // 把明文写入文件in.file
    FILE *outPlainFile = fopen("./in.file", "wb");
    if (outPlainFile == NULL) {
        /* Error */
        return 0;
    }
    fwrite(inBuf, 1, sizeof(inBuf), outPlainFile);
    fclose(outPlainFile);

    // 把密文写入文件enc_message
    FILE *outEncFile = fopen("./enc_message", "wb");
    if (outEncFile == NULL) {
        /* Error */
        return 0;
    }
    fwrite(enc_out, 1, sizeof(enc_out), outEncFile);
    fclose(outEncFile);
}


void print_data(const char *title, const void *data, int len) {
    printf("%s ", title);
    const unsigned char *p = (const unsigned char *) data;
    for (int i = 0; i < len; i++) {
        printf("%02X ", *p++);
    }

    printf("\n");
}
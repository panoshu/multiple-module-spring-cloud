package com.example.file.domain.gateway;

/**
 * 文件存储结果
 *
 * @param storageKey 实际存储 key
 * @param md5        MD5 校验值
 */
public record StoreResult(String storageKey, String md5) {
}

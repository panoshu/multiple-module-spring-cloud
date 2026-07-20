package com.example.file.domain.gateway;

/**
 * 文件存储结果
 *
 * @param storageKey 实际存储 key
 * @param digest     内容摘要（SM3）
 */
public record StoreResult(String storageKey, String digest) {
}

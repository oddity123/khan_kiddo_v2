package com.khankiddo.learning.extensionconnect;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ExtensionCredentialMapper {

    int insert(ExtensionCredential credential);

    Optional<ExtensionCredential> findActiveByTokenHash(@Param("tokenHash") String tokenHash,
                                                        @Param("now") LocalDateTime now);

    Optional<ExtensionCredential> findByIdAndUserId(@Param("id") long id, @Param("userId") long userId);

    List<ExtensionCredential> findActiveByUserId(@Param("userId") long userId, @Param("now") LocalDateTime now);

    int revokeById(@Param("id") long id,
                   @Param("userId") long userId,
                   @Param("revokedAt") LocalDateTime revokedAt);

    int revokeAllActiveByUserId(@Param("userId") long userId, @Param("revokedAt") LocalDateTime revokedAt);

    int touchLastUsed(@Param("id") long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);
}

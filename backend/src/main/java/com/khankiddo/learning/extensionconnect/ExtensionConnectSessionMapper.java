package com.khankiddo.learning.extensionconnect;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface ExtensionConnectSessionMapper {

    int insert(ExtensionConnectSession session);

    Optional<ExtensionConnectSession> findById(@Param("id") String id);

    int markAuthorized(@Param("id") String id,
                       @Param("userId") long userId,
                       @Param("authorizedAt") LocalDateTime authorizedAt,
                       @Param("expectedStatus") String expectedStatus);

    int markDenied(@Param("id") String id,
                   @Param("expectedStatus") String expectedStatus);

    int markExchanged(@Param("id") String id,
                      @Param("exchangedAt") LocalDateTime exchangedAt,
                      @Param("expectedStatus") String expectedStatus);
}

package com.campusoverflow.identity.application;

/** 用户更新自己资料的命令。 */
public record UpdateProfileCommand(String displayName, String email) {
}

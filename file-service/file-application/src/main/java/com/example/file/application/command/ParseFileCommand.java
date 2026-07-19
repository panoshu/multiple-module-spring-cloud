package com.example.file.application.command;

public record ParseFileCommand(
    String fileTaskId,
    String operator
) {}

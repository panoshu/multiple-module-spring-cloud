package com.example.file.domain.model.locator;

import com.example.file.domain.model.enums.Direction;

public record AnchorRelativeLocator(String anchorText, Direction direction, int offset) implements Locator {
}

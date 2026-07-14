package com.example.file.domain.model.locator;

public sealed interface Locator permits AbsoluteLocator, AnchorRelativeLocator, HeaderMatchLocator, RegionRelativeLocator {
}

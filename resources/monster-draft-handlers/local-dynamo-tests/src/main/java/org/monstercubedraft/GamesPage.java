package org.monstercubedraft;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public enum GamesPage {

    INDEX("//", "NDX"),
    DATA0("//", "da0"),
    DATA1("//", "da1"),
    DATA2("//", "da2");
    private final String namespace;
    private final String page;

    GamesPage(String namespace, String page) {
        this.namespace = namespace;
        this.page = page;
    }

    @Override
    public String toString() {
        return String.format("%s%s", namespace, page);
    }

    public AttributeValue asAttributeValue() {
        return AttributeValue.fromS(this.toString());
    }
}

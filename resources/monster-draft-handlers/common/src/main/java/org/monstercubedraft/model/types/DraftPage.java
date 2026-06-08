package org.monstercubedraft.model.types;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public enum DraftPage {
  INDEX("//", "NDX"),
  DATA0("//", "da0"),
  DATA1("//", "da1"),
  DATA2("//", "da2");

  private final String namespace;
  private final String page;

  DraftPage(String namespace, String page) {
    this.namespace = namespace;
    this.page = page;
  }

  @Override
  public String toString() {
    return String.format("%s%s", namespace, page);
  }

  public String getNamespace() {
    return namespace;
  }

  public AttributeValue asAttributeValue() {
    return AttributeValue.fromS(this.toString());
  }
}

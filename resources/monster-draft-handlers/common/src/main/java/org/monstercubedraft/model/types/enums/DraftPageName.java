package org.monstercubedraft.model.types.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public enum DraftPageName {
  INDEX("//", "NDX"),
  DATA0("//", "da0"),
  DATA1("//", "da1"),
  DATA2("//", "da2");

  private final String namespace;
  private final String page;

  private static final Map<String, DraftPageName> stringsToEnums =
      Arrays.stream(DraftPageName.values())
          .collect(Collectors.toMap(e -> e.namespace + e.page, Function.identity()));

  DraftPageName(String namespace, String page) {
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

  public static DraftPageName fromString(String s) {
    DraftPageName pageOut = stringsToEnums.get(s);
    if (pageOut == null) throw new IllegalArgumentException("'%s' not a valid draft page");
    return pageOut;
  }

  public static DraftPageName fromAttributeValue(AttributeValue av) {
    return fromString(av.s());
  }
}

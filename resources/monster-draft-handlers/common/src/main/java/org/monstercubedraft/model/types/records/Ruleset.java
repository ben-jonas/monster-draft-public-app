package org.monstercubedraft.model.types.records;

/**
 * A stub. Eventually will represent some CSV data uploaded to S3, which traces the available cards,
 * their mappings from b64 keys which are stored in the Draft pages in Dynamo, the card upgrade
 * paths, and other card metadata, and possibly some default starting settings for the draft.
 */
public record Ruleset() {}

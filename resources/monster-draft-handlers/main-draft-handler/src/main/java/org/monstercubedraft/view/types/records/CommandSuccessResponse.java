package org.monstercubedraft.view.types.records;

import org.monstercubedraft.controller.types.CommandId;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record CommandSuccessResponse<T>(CommandId commandId, T output) {}

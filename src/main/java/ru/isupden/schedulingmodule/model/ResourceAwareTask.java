package ru.isupden.schedulingmodule.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("RESOURCE")
public class ResourceAwareTask extends Task {
    private Map<String, Integer> requiredResources;
}

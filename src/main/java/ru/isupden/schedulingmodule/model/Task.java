package ru.isupden.schedulingmodule.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PriorityTask.class,   name = "PRIORITY"),
        @JsonSubTypes.Type(value = DeadlineBasedTask.class,   name = "DEADLINE"),
        @JsonSubTypes.Type(value = ResourceAwareTask.class,   name = "RESOURCE"),
        @JsonSubTypes.Type(value = FairnessOrientedTask.class,   name = "FAIRNESS"),
})
public abstract class Task {
    protected String workflowType;
    protected String workflowId;
    protected Map<String, Object> payload;
}

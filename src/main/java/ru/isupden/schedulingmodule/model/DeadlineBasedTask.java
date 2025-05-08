package ru.isupden.schedulingmodule.model;

import java.time.Instant;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonTypeName("DEADLINE")
public class DeadlineBasedTask extends Task {
    private Instant deadline;
    private Date deadlineDate = new Date();
}

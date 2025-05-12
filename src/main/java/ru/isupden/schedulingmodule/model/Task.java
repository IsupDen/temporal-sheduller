package ru.isupden.schedulingmodule.model;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Task {

    /**
     * Тип воркфлоу, который нужно запустить.
     */
    private String workflowType;

    /**
     * WorkflowId для child-воркфлоу.
     */
    private String workflowId;

    /**
     * Произвольный payload, который передаётся в child-воркфлоу.
     */
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Builder.Default
    @Getter
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * Утилита: получить атрибут нужного типа или null.
     */
    @SuppressWarnings("unchecked")
    public <T> T attr(String key, Class<T> type) {
        Object o = attributes.get(key);
        return type.isInstance(o) ? (T) o : null;
    }
}

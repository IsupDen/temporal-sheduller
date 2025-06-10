# Scheduling Module

Библиотека для умного планирования и диспетчеризации задач с использованием Temporal Workflow Engine. Поддерживает различные алгоритмы планирования, метрики производительности и контроль нагрузки.

## Содержание

- [Быстрый старт](#быстрый-старт)
- [Конфигурация](#конфигурация)
- [Модель данных](#модель-данных)
- [Стратегии планирования](#стратегии-планирования)
- [Workflow интерфейс](#workflow-интерфейс)
- [Метрики и мониторинг](#метрики-и-мониторинг)
- [Примеры использования](#примеры-использования)
- [Продвинутые настройки](#продвинутые-настройки)

## Быстрый старт

### Подключение зависимости

Добавьте зависимость в ваш `build.gradle`:

```gradle
dependencies {
    implementation 'ru.isupden:scheduling-module:0.0.12-SNAPSHOT'
}
```

### Включение модуля

Добавьте аннотацию [`@EnableSchedulingModule`](src/main/java/ru/isupden/schedulingmodule/annotation/EnableSchedulingModule.java:16) к вашему Spring Boot приложению:

```java
@SpringBootApplication
@EnableSchedulingModule
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Базовая конфигурация

Добавьте конфигурацию в `application.yml`:

```yaml
scheduling-module:
  namespace: "default"
  target: "127.0.0.1:7233"
  clients:
    my-client:
      task-queue: "my-queue"
      strategy: "priority"
```

## Конфигурация

Библиотека использует [`SchedulingModuleProperties`](src/main/java/ru/isupden/schedulingmodule/config/SchedulingModuleProperties.java:14) для конфигурации через YAML с префиксом `scheduling-module`.

### Основные параметры

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|--------------|----------|
| `namespace` | String | "default" | Temporal namespace |
| `target` | String | "127.0.0.1:7233" | Адрес Temporal сервера |
| `clients` | Map | {} | Конфигурация клиентов |
| `backpressure` | Object | - | Настройки контроля нагрузки |
| `fairness` | Object | - | Настройки справедливого распределения |
| `quotas` | Map | {} | Квоты для тенантов |

### Конфигурация клиентов

Каждый клиент имеет следующие параметры:

```yaml
scheduling-module:
  clients:
    client-name:
      task-queue: "queue-name"    # Имя очереди задач
      strategy: "priority"        # Стратегия планирования
```

### Настройки Back-pressure

```yaml
scheduling-module:
  backpressure:
    window-seconds: 60          # Окно для sliding window (сек)
    throughput-factor: 10       # Лимит задач в секунду
```

### Настройки Fairness

```yaml
scheduling-module:
  fairness:
    half-life-seconds: 3600     # Полувремя затухания EWMA (сек)
```

### Квоты тенантов

```yaml
scheduling-module:
  quotas:
    tenant-1: 0.5              # 50% ресурсов
    tenant-2: 0.3              # 30% ресурсов
    tenant-3: 0.2              # 20% ресурсов
```

## Модель данных

### Task

Основная модель задачи [`Task`](src/main/java/ru/isupden/schedulingmodule/model/Task.java:16):

```java
@Data
@Builder
public class Task {
    private String workflowType;           // Тип workflow для выполнения
    private String workflowId;             // Уникальный ID workflow
    private Map<String, Object> payload;   // Данные для передачи в workflow
    private Map<String, Object> attributes; // Атрибуты для планирования
    
    // Утилитный метод для получения типизированных атрибутов
    public <T> T attr(String key, Class<T> type);
}
```

### Атрибуты планирования

Библиотека использует атрибуты для принятия решений о планировании:

| Атрибут | Тип | Описание |
|---------|-----|----------|
| `priority` | Integer | Приоритет задачи (чем больше, тем важнее) |
| `deadline` | Instant | Крайний срок выполнения |
| `tenant` | String | Идентификатор тенанта для fairness |
| `estimatedDuration` | Duration | Ожидаемое время выполнения |
| `dependencies` | List<String> | Зависимости для critical path |

## Стратегии планирования

Библиотека поддерживает несколько стратегий планирования, реализующих интерфейс [`SchedulingStrategy`](src/main/java/ru/isupden/schedulingmodule/strategy/SchedulingStrategy.java:11):

### Базовые стратегии

#### Priority Strategy
Планирование по приоритету задач:

```yaml
strategy: "priority"
```

```java
// Установка приоритета
Task task = Task.builder()
    .workflowType("MyWorkflow")
    .workflowId("task-1")
    .build();
task.getAttributes().put("priority", 10);
```

#### Deadline Strategy
Планирование по крайним срокам (EDF - Earliest Deadline First):

```yaml
strategy: "deadline"
```

```java
// Установка дедлайна
Task task = Task.builder()
    .workflowType("MyWorkflow")
    .workflowId("task-1")
    .build();
task.getAttributes().put("deadline", Instant.now().plusSeconds(3600));
```

#### Fairness Strategy
Справедливое распределение ресурсов между тенантами:

```yaml
strategy: "fairness"
```

```java
// Указание тенанта
Task task = Task.builder()
    .workflowType("MyWorkflow")
    .workflowId("task-1")
    .build();
task.getAttributes().put("tenant", "tenant-1");
```

#### Critical Path Strategy
Планирование по критическому пути:

```yaml
strategy: "critical-path"
```

```java
// Установка зависимостей и времени выполнения
Task task = Task.builder()
    .workflowType("MyWorkflow")
    .workflowId("task-1")
    .build();
task.getAttributes().put("estimatedDuration", Duration.ofMinutes(30));
task.getAttributes().put("dependencies", Arrays.asList("task-0"));
```

### Композитные стратегии

Можно комбинировать несколько стратегий через символ `+`:

```yaml
strategy: "priority+deadline"        # Сначала приоритет, потом дедлайн
strategy: "fairness+priority"        # Сначала fairness, потом приоритет
strategy: "deadline+critical-path"   # Комбинация дедлайна и критического пути
```

## Workflow интерфейс

### SchedulerWorkflow

Основной интерфейс для работы с планировщиком [`SchedulerWorkflow`](src/main/java/ru/isupden/schedulingmodule/workflow/SchedulerWorkflow.java:12):

```java
@WorkflowInterface
public interface SchedulerWorkflow {
    
    @WorkflowMethod
    void run(String clientName);
    
    @SignalMethod
    void submitTasks(List<Task> tasks, String clientName);
    
    @QueryMethod
    int getQueueLength();
    
    @SignalMethod
    void reportUsage(String tenant, double cost);
}
```

### Использование

```java
@Autowired
private WorkflowClient workflowClient;

// Запуск планировщика
SchedulerWorkflow scheduler = workflowClient.newWorkflowStub(
    SchedulerWorkflow.class,
    WorkflowOptions.newBuilder()
        .setWorkflowId("scheduler-my-client")
        .setTaskQueue("my-queue")
        .build()
);

// Асинхронный запуск
WorkflowExecution execution = WorkflowClient.start(scheduler::run, "my-client");

// Отправка задач
List<Task> tasks = Arrays.asList(
    Task.builder()
        .workflowType("ProcessData")
        .workflowId("data-task-1")
        .build()
);
scheduler.submitTasks(tasks, "my-client");

// Запрос текущей длины очереди
int queueLength = scheduler.getQueueLength();
```

## Метрики и мониторинг

Библиотека предоставляет метрики через Micrometer для мониторинга производительности:

### Доступные метрики

- `scheduler.tasks.submitted` - Количество отправленных задач
- `scheduler.tasks.dispatched` - Количество выполненных задач
- `scheduler.queue.length` - Текущая длина очереди
- `scheduler.backpressure.active` - Активация back-pressure
- `scheduler.fairness.usage` - Использование ресурсов по тенантам

### Prometheus конфигурация

Добавьте в `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health,metrics"
  metrics:
    export:
      prometheus:
        enabled: true
```

### Grafana Dashboard

Пример запросов для создания дашборда:

```promql
# Пропускная способность
rate(scheduler_tasks_dispatched_total[5m])

# Длина очереди
scheduler_queue_length

# Использование по тенантам
scheduler_fairness_usage{tenant="tenant-1"}
```

## Примеры использования

### Простой планировщик приоритетов

```java
// application.yml
scheduling-module:
  clients:
    priority-client:
      task-queue: "priority-queue"
      strategy: "priority"

// Java код
@Service
public class TaskService {
    
    @Autowired
    private WorkflowClient workflowClient;
    
    public void submitHighPriorityTask(String workflowType, String workflowId) {
        SchedulerWorkflow scheduler = getScheduler("priority-client");
        
        Task task = Task.builder()
            .workflowType(workflowType)
            .workflowId(workflowId)
            .build();
        task.getAttributes().put("priority", 100);
        
        scheduler.submitTasks(Arrays.asList(task), "priority-client");
    }
    
    private SchedulerWorkflow getScheduler(String clientName) {
        return workflowClient.newWorkflowStub(
            SchedulerWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId("scheduler-" + clientName)
                .setTaskQueue(getTaskQueue(clientName))
                .build()
        );
    }
}
```

### Многотенантный планировщик

```java
// application.yml
scheduling-module:
  clients:
    multitenant-client:
      task-queue: "multitenant-queue"
      strategy: "fairness+priority"
  fairness:
    half-life-seconds: 1800
  quotas:
    tenant-a: 0.6
    tenant-b: 0.4

// Java код
@Service
public class MultiTenantTaskService {
    
    public void submitTenantTask(String tenant, String workflowType, int priority) {
        SchedulerWorkflow scheduler = getScheduler("multitenant-client");
        
        Task task = Task.builder()
            .workflowType(workflowType)
            .workflowId(tenant + "-" + UUID.randomUUID())
            .build();
        task.getAttributes().put("tenant", tenant);
        task.getAttributes().put("priority", priority);
        
        scheduler.submitTasks(Arrays.asList(task), "multitenant-client");
    }
}
```

### Планировщик с дедлайнами

```java
// application.yml
scheduling-module:
  clients:
    deadline-client:
      task-queue: "deadline-queue"
      strategy: "deadline+priority"

// Java код
@Service
public class DeadlineTaskService {
    
    public void submitUrgentTask(String workflowType, Duration timeToDeadline) {
        SchedulerWorkflow scheduler = getScheduler("deadline-client");
        
        Task task = Task.builder()
            .workflowType(workflowType)
            .workflowId("urgent-" + System.currentTimeMillis())
            .build();
        task.getAttributes().put("deadline", Instant.now().plus(timeToDeadline));
        task.getAttributes().put("priority", 50);
        
        scheduler.submitTasks(Arrays.asList(task), "deadline-client");
    }
}
```

## Продвинутые настройки

### Создание пользовательской стратегии

Для создания собственной стратегии планирования:

```java
@Component
public class CustomSchedulingStrategy implements SchedulingStrategy {
    
    @Override
    public boolean canCompare(Task a, Task b) {
        // Проверяем, можем ли сравнить эти задачи
        return a.attr("customAttribute", String.class) != null &&
               b.attr("customAttribute", String.class) != null;
    }
    
    @Override
    public int compare(Task a, Task b) {
        String attrA = a.attr("customAttribute", String.class);
        String attrB = b.attr("customAttribute", String.class);
        return attrA.compareTo(attrB);
    }
    
    @Override
    public void preprocess(Queue<Task> queue, Instant now) {
        // Предобработка очереди (например, удаление устаревших задач)
    }
    
    @Override
    public void onDispatch(Task task, Instant at) {
        // Действия после успешного запуска задачи
    }
}
```

### Настройка интерцепторов

Для добавления логики мониторинга или логирования:

```java
@Configuration
public class SchedulingConfiguration {
    
    @Bean
    public WorkerFactoryOptions workerFactoryOptions() {
        return WorkerFactoryOptions.newBuilder()
            .setWorkerInterceptors(new UsageReportInterceptor())
            .build();
    }
}
```

### Тестирование

Для unit-тестирования используйте Temporal Testing framework:

```java
@Test
public void testSchedulerWorkflow() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker("test-queue");
    worker.registerWorkflowImplementationTypes(SchedulerWorkflowImpl.class);
    
    testEnv.start();
    
    SchedulerWorkflow scheduler = testEnv.getWorkflowClient()
        .newWorkflowStub(SchedulerWorkflow.class);
    
    // Тест логики планирования
    List<Task> tasks = createTestTasks();
    scheduler.submitTasks(tasks, "test-client");
    
    assertEquals(tasks.size(), scheduler.getQueueLength());
    
    testEnv.close();
}
```

## Устранение неполадок

### Частые проблемы

1. **Temporal сервер недоступен**
   ```
   Ошибка: io.grpc.StatusRuntimeException: UNAVAILABLE
   Решение: Проверьте настройку target в конфигурации
   ```

2. **Задачи не выполняются**
   ```
   Проверьте:
   - Соответствие task-queue в конфигурации и воркере
   - Регистрацию workflow implementations
   - Права доступа к namespace
   ```

3. **Высокое потребление памяти**
   ```
   Настройте back-pressure:
   backpressure:
     window-seconds: 30
     throughput-factor: 5
   ```

### Логирование

Включите debug логирование для диагностики:

```yaml
logging:
  level:
    ru.isupden.schedulingmodule: DEBUG
    io.temporal: INFO
```

## Лицензия

Эта библиотека распространяется под лицензией, указанной в файле LICENSE.

## Поддержка

Для вопросов и предложений создавайте issues в GitHub репозитории проекта.